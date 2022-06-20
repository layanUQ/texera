package edu.uci.ics.texera.web.resource.dashboard.project

import edu.uci.ics.texera.web.SqlServer
import edu.uci.ics.texera.web.auth.SessionUser
import edu.uci.ics.texera.web.model.jooq.generated.Tables.{
  FILE,
  FILE_OF_PROJECT,
  USER,
  USER_FILE_ACCESS,
  USER_PROJECT,
  WORKFLOW,
  WORKFLOW_OF_PROJECT,
  WORKFLOW_OF_USER,
  WORKFLOW_USER_ACCESS
}
import edu.uci.ics.texera.web.model.jooq.generated.tables.daos.{
  FileOfProjectDao,
  UserProjectDao,
  WorkflowOfProjectDao
}
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.{
  File,
  FileOfProject,
  UserFileAccess,
  UserProject,
  Workflow,
  WorkflowOfProject,
  WorkflowUserAccess
}
import edu.uci.ics.texera.web.resource.dashboard.project.ProjectResource.{
  context,
  fileOfProjectDao,
  userProjectDao,
  workflowOfProjectDao,
  workflowOfProjectExists
}
import edu.uci.ics.texera.web.resource.dashboard.file.UserFileResource.DashboardFileEntry
import edu.uci.ics.texera.web.resource.dashboard.workflow.WorkflowAccessResource.toAccessLevel
import edu.uci.ics.texera.web.resource.dashboard.workflow.WorkflowResource.DashboardWorkflowEntry
import org.jooq.types.UInteger

import javax.ws.rs._
import javax.ws.rs.core.MediaType
import java.util
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import io.dropwizard.auth.Auth
import org.apache.commons.lang3.StringUtils

import javax.annotation.security.PermitAll

/**
  * This file handles various request related to projects.
  * It sends mysql queries to the MysqlDB regarding the 'user_project',
  * 'workflow_of_project', and 'file_of_project' Tables
  * The details of these tables can be found in /core/scripts/sql/texera_ddl.sql
  */

object ProjectResource {
  final private val context = SqlServer.createDSLContext()
  final private val userProjectDao = new UserProjectDao(context.configuration)
  final private val workflowOfProjectDao = new WorkflowOfProjectDao(context.configuration)
  final private val fileOfProjectDao = new FileOfProjectDao(context.configuration)

  private def workflowOfProjectExists(wid: UInteger, pid: UInteger): Boolean = {
    workflowOfProjectDao.existsById(
      context
        .newRecord(WORKFLOW_OF_PROJECT.WID, WORKFLOW_OF_PROJECT.PID)
        .values(wid, pid)
    )
  }

  /**
    * This method is used to insert any CSV files created from ResultExportService
    * handleCSVRequest function into all project(s) that the workflow belongs to.
    *
    * No insertion occurs if the workflow does not belong to any projects.
    *
    * @param uid user ID
    * @param wid workflow ID
    * @param fileName name of exported file
    * @return String containing status of adding exported file to project(s)
    */
  def addExportedFileToProject(uid: UInteger, wid: UInteger, fileName: String): String = {
    // get map of PIDs and project names
    val pidMap = context
      .select(WORKFLOW_OF_PROJECT.PID, USER_PROJECT.NAME)
      .from(WORKFLOW_OF_PROJECT)
      .leftJoin(USER_PROJECT)
      .on(WORKFLOW_OF_PROJECT.PID.eq(USER_PROJECT.PID))
      .where(WORKFLOW_OF_PROJECT.WID.eq(wid))
      .fetch()
      .intoMap(WORKFLOW_OF_PROJECT.PID, USER_PROJECT.NAME)

    if (pidMap.size() > 0) { // workflow belongs to project(s)
      // get fid using fileName & cast to UInteger
      val fid = context
        .select(FILE.FID)
        .from(FILE)
        .where(FILE.UID.eq(uid).and(FILE.NAME.eq(fileName)))
        .fetchOneInto(FILE)
        .getFid

      // add file to all projects this workflow belongs to
      pidMap
        .keySet()
        .forEach((pid: UInteger) => fileOfProjectDao.insert(new FileOfProject(fid, pid)))

      // generate string for ResultExportResponse
      if (pidMap.size() == 1) {
        s"and added to project: ${pidMap.values().toArray()(0)}"
      } else {
        s"and added to projects: ${pidMap.values().mkString(", ")}"
      }
    } else { // workflow does not belong to a project
      ""
    }
  }
}

@Path("/project")
@PermitAll
@Produces(Array(MediaType.APPLICATION_JSON))
class ProjectResource {

  /**
    * This method returns the specified project.
    *
    * @param pid project id
    * @return project specified by the project id
    */
  @GET
  @Path("/{pid}")
  def getProject(@PathParam("pid") pid: UInteger): UserProject = {
    userProjectDao.fetchOneByPid(pid)
  }

  /**
    * This method returns the list of projects owned by the session user.
    *
    * @param sessionUser the session user
    * @return a list of projects belonging to owner
    */
  @GET
  @Path("/list")
  def listProjectsOwnedByUser(@Auth sessionUser: SessionUser): util.List[UserProject] = {
    val oid = sessionUser.getUser.getUid
    userProjectDao.fetchByOwnerId(oid)
  }

  /**
    * This method returns a list of DashboardWorkflowEntry objects, which represents
    * all the workflows that are part of the specified project.
    *
    * @param pid project ID
    * @param sessionUser the session user
    * @return list of DashboardWorkflowEntry objects
    */
  @GET
  @Path("/{pid}/workflows")
  def listProjectWorkflows(
      @PathParam("pid") pid: UInteger,
      @Auth sessionUser: SessionUser
  ): List[DashboardWorkflowEntry] = {
    val uid = sessionUser.getUser.getUid
    val workflowEntries = context
      .select(
        WORKFLOW.WID,
        WORKFLOW.NAME,
        WORKFLOW.CREATION_TIME,
        WORKFLOW.LAST_MODIFIED_TIME,
        WORKFLOW_USER_ACCESS.READ_PRIVILEGE,
        WORKFLOW_USER_ACCESS.WRITE_PRIVILEGE,
        WORKFLOW_OF_USER.UID,
        USER.NAME
      )
      .from(WORKFLOW_OF_PROJECT)
      .leftJoin(WORKFLOW)
      .on(WORKFLOW.WID.eq(WORKFLOW_OF_PROJECT.WID))
      .leftJoin(WORKFLOW_USER_ACCESS)
      .on(WORKFLOW_USER_ACCESS.WID.eq(WORKFLOW_OF_PROJECT.WID))
      .leftJoin(WORKFLOW_OF_USER)
      .on(WORKFLOW_OF_USER.WID.eq(WORKFLOW_OF_PROJECT.WID))
      .leftJoin(USER)
      .on(USER.UID.eq(WORKFLOW_OF_USER.UID))
      .where(WORKFLOW_OF_PROJECT.PID.eq(pid).and(WORKFLOW_USER_ACCESS.UID.eq(uid)))
      .fetch()
    workflowEntries
      .map(workflowRecord =>
        DashboardWorkflowEntry(
          workflowRecord.into(WORKFLOW_OF_USER).getUid.eq(uid),
          toAccessLevel(
            workflowRecord.into(WORKFLOW_USER_ACCESS).into(classOf[WorkflowUserAccess])
          ).toString,
          workflowRecord.into(USER).getName,
          workflowRecord.into(WORKFLOW).into(classOf[Workflow]),
          workflowOfProjectDao
            .fetchByWid(workflowRecord.into(WORKFLOW).getWid)
            .map(workflowOfProject => workflowOfProject.getPid)
            .toList
        )
      )
      .toList
  }

  /**
    * This method returns a list of DashboardFileEntry objects, which represents
    * all the file objects that are part of the specified project.
    *
    * @param pid project ID
    * @param sessionUser the session user
    * @return a list of DashboardFileEntry objects
    */
  @GET
  @Path("/{pid}/files")
  def listProjectFiles(
      @PathParam("pid") pid: UInteger,
      @Auth sessionUser: SessionUser
  ): List[DashboardFileEntry] = {
    val user = sessionUser.getUser
    val fileEntries = context
      .select(
        FILE.FID,
        FILE.SIZE,
        FILE.NAME,
        FILE.PATH,
        FILE.DESCRIPTION,
        USER_FILE_ACCESS.READ_ACCESS,
        USER_FILE_ACCESS.WRITE_ACCESS,
        USER.NAME // owner name
      )
      .from(FILE_OF_PROJECT)
      .leftJoin(FILE)
      .on(FILE.FID.eq(FILE_OF_PROJECT.FID))
      .leftJoin(USER_FILE_ACCESS)
      .on(USER_FILE_ACCESS.FID.eq(FILE_OF_PROJECT.FID))
      .leftJoin(USER)
      .on(USER.UID.eq(FILE.UID))
      .where(FILE_OF_PROJECT.PID.eq(pid).and(USER_FILE_ACCESS.UID.eq(user.getUid)))
      .fetch()
    fileEntries
      .map(fileRecord =>
        DashboardFileEntry(
          fileRecord.into(USER).getName,
          toFileAccessLevel(fileRecord.into(USER_FILE_ACCESS).into(classOf[UserFileAccess])),
          fileRecord.into(USER).getName == user.getName,
          fileRecord.into(FILE).into(classOf[File]),
          fileOfProjectDao
            .fetchByFid(fileRecord.into(FILE).getFid)
            .map(fileOfProject => fileOfProject.getPid)
            .toList
        )
      )
      .toList
  }

  /**
    * This is a helper function used in creating DashboardFileEntry objects.
    * It extracts the access level given a UserFileAccess generated POJO
    *
    * @param userFileAccess the UserFileAccess object
    * @return
    */
  def toFileAccessLevel(userFileAccess: UserFileAccess): String = {
    if (userFileAccess.getWriteAccess) {
      "Write"
    } else if (userFileAccess.getReadAccess) {
      "Read"
    } else {
      "None"
    }
  }

  /**
    * This method inserts a new project into the database belonging to the session user
    * and with the specified name.
    *
    * @param sessionUser the session user
    * @param name project name
    */
  @POST
  @Path("/create/{name}")
  def createProject(
      @Auth sessionUser: SessionUser,
      @PathParam("name") name: String
  ): UserProject = {
    val oid = sessionUser.getUser.getUid

    val userProject = new UserProject(null, name, oid, null, null)
    try {
      userProjectDao.insert(userProject)
    } catch {
      case _: Throwable =>
        throw new BadRequestException("Cannot create a new project with provided name.");
    }
    userProjectDao.fetchOneByPid(userProject.getPid)
  }

  /**
    * This method adds a mapping between the specified workflow to the specified project
    * into the database.
    *
    * @param pid project ID
    * @param wid workflow ID
    */
  @POST
  @Path("/{pid}/workflow/{wid}/add")
  def addWorkflowToProject(
      @PathParam("pid") pid: UInteger,
      @PathParam("wid") wid: UInteger
  ): Unit = {
    if (!workflowOfProjectExists(wid, pid)) {
      workflowOfProjectDao.insert(new WorkflowOfProject(wid, pid))
    }
  }

  /**
    * This method adds a mapping between the specified file to the specified project
    * into the database
    *
    * @param pid project ID
    * @param fid file ID
    */
  @POST
  @Path("/{pid}/user-file/{fid}/add")
  def addFileToProject(@PathParam("pid") pid: UInteger, @PathParam("fid") fid: UInteger): Unit = {
    fileOfProjectDao.insert(new FileOfProject(fid, pid))
  }

  /**
    * This method updates the project name of the specified, existing project
    *
    * @param pid project ID
    * @param name new name
    */
  @POST
  @Path("/{pid}/rename/{name}")
  def updateProjectName(@PathParam("pid") pid: UInteger, @PathParam("name") name: String): Unit = {
    if (StringUtils.isBlank(name)) {
      throw new BadRequestException("Cannot rename project to empty or blank name.")
    }

    val userProject = userProjectDao.fetchOneByPid(pid)
    try {
      userProject.setName(name)
      userProjectDao.update(userProject)
    } catch {
      case _: Throwable => throw new BadRequestException("Cannot rename project to provided name.");
    }
  }

  /**
    * This method updates a project's color.
    *
    * @param pid id of project to be updated
    * @param colorHex new HEX formatted color to be set
    */
  @POST
  @Path("/{pid}/color/{colorHex}/add")
  def updateProjectColor(
      @PathParam("pid") pid: UInteger,
      @PathParam("colorHex") colorHex: String
  ): Unit = {
    if (
      colorHex == null || colorHex.length != 6 && colorHex.length != 3 || !colorHex.matches(
        "^[A-Fa-f0-9]{6}|[A-Fa-f0-9]{3}$"
      )
    ) {
      throw new BadRequestException("Cannot assign invalid HEX format color to project.")
    }

    val userProject = userProjectDao.fetchOneByPid(pid)
    userProject.setColor(colorHex)
    userProjectDao.update(userProject)
  }

  @POST
  @Path("/{pid}/color/delete")
  def deleteProjectColor(@PathParam("pid") pid: UInteger): Unit = {
    val userProject = userProjectDao.fetchOneByPid(pid)
    userProject.setColor(null)
    userProjectDao.update(userProject)
  }

  /**
    * This method deletes an existing project from the database
    *
    * @param pid projectID
    */
  @DELETE
  @Path("/delete/{pid}")
  def deleteProject(@PathParam("pid") pid: UInteger): Unit = {
    userProjectDao.deleteById(pid)
  }

  /**
    * This method deletes an existing mapping between a workflow and project from
    * the database
    *
    * @param pid project ID
    * @param wid workflow ID
    */
  @DELETE
  @Path("/{pid}/workflow/{wid}/delete")
  def deleteWorkflowFromProject(
      @PathParam("pid") pid: UInteger,
      @PathParam("wid") wid: UInteger
  ): Unit = {
    workflowOfProjectDao.deleteById(
      context.newRecord(WORKFLOW_OF_PROJECT.WID, WORKFLOW_OF_PROJECT.PID).values(wid, pid)
    )
  }

  /**
    * This method deletes an existing mapping between a file and a project from
    * the database
    *
    * @param pid project ID
    * @param fid file ID
    */
  @DELETE
  @Path("/{pid}/user-file/{fid}/delete")
  def deleteFileFromProject(
      @PathParam("pid") pid: UInteger,
      @PathParam("fid") fid: UInteger
  ): Unit = {
    fileOfProjectDao.deleteById(
      context.newRecord(FILE_OF_PROJECT.FID, FILE_OF_PROJECT.PID).values(fid, pid)
    )
  }

}
