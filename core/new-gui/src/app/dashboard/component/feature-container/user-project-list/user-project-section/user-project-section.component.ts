import { Component, OnInit } from "@angular/core";
import { UserProjectService } from "../../../../service/user-project/user-project.service";
import { ActivatedRoute } from "@angular/router";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { NgbdModalAddProjectFileComponent } from "./ngbd-modal-add-project-file/ngbd-modal-add-project-file.component";
import { NgbdModalRemoveProjectFileComponent } from "./ngbd-modal-remove-project-file/ngbd-modal-remove-project-file.component";
import { DashboardUserFileEntry } from "../../../../type/dashboard-user-file-entry";

// ---- for file card
import { NotificationService } from "../../../../../common/service/notification/notification.service";
import { UserFileService } from "../../../../service/user-file/user-file.service";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { NgbdModalUserFileShareAccessComponent } from "../../user-file-section/ngbd-modal-file-share-access/ngbd-modal-user-file-share-access.component";
import { UserProject } from "../../../../type/user-project";

export const ROUTER_WORKFLOW_BASE_URL = "/workflow";
export const ROUTER_USER_PROJECT_BASE_URL = "/dashboard/user-project";

@UntilDestroy()
@Component({
  selector: "texera-user-project-section",
  templateUrl: "./user-project-section.component.html",
  styleUrls: ["./user-project-section.component.scss"],
})
export class UserProjectSectionComponent implements OnInit {
  // information from the database about this project
  public pid: number = 0;
  public name: string = "";
  public ownerID: number = 0;
  public creationTime: number = 0;
  public color: string | null = null;

  // information for modifying project color
  public inputColor: string = "#ffffff"; // needs to have a '#' in front, as it is used by ngx-color-picker
  public colorIsBright: boolean = false;
  public projectDataIsLoaded: boolean = false;
  public colorPickerIsSelected: boolean = false;
  public updateProjectStatus = ""; // track any updates to user project for child components to rerender

  // temporarily here for file section color tags, TODO : remove once file service PR approved
  public userProjectsMap: ReadonlyMap<number, UserProject> = new Map(); // maps pid to its corresponding UserProject
  public colorBrightnessMap: ReadonlyMap<number, boolean> = new Map(); // tracks whether each project's color is light or dark

  // ----- for file card
  public isEditingFileName: number[] = [];

  constructor(
    private userProjectService: UserProjectService,
    private activatedRoute: ActivatedRoute,
    private router: Router,
    private modalService: NgbModal,
    private userFileService: UserFileService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    // extract passed PID from parameter and re-render page if necessary
    this.activatedRoute.url.pipe(untilDestroyed(this)).subscribe(url => {
      if (url.length == 2 && url[1].path) {
        this.pid = parseInt(url[1].path);

        this.getUserProjectMetadata();
        this.userProjectService.refreshFilesOfProject(this.pid); // TODO : remove after refactoring file section
      }
    });

    // otherwise no project ID, no project to load
  }

  public onClickOpenAddFile() {
    const modalRef = this.modalService.open(NgbdModalAddProjectFileComponent);
    modalRef.componentInstance.addedFiles = this.getUserProjectFilesArray();
    modalRef.componentInstance.projectId = this.pid;
  }

  public onClickOpenRemoveFile() {
    const modalRef = this.modalService.open(NgbdModalRemoveProjectFileComponent);
    modalRef.componentInstance.addedFiles = this.getUserProjectFilesArray();
    modalRef.componentInstance.projectId = this.pid;
  }

  private getUserProjectMetadata() {
    // TODO : temporarily removed, revert back to retrieving data for just a single project after future PR to reuse UserFileSection component
    // this.userProjectService
    //   .retrieveProject(this.pid)
    //   .pipe(untilDestroyed(this))
    //   .subscribe(project => {
    //     this.name = project.name;
    //     this.ownerID = project.ownerID;
    //     this.creationTime = project.creationTime;
    //     if (project.color != null) {
    //       this.color = project.color;
    //       this.inputColor = "#" + project.color;
    //       this.colorIsBright = this.userProjectService.isLightColor(project.color);
    //     }
    //     this.projectDataIsLoaded = true;
    //   });

    this.userProjectService
      .retrieveProjectList()
      .pipe(untilDestroyed(this))
      .subscribe(userProjectList => {
        if (userProjectList != null && userProjectList.length > 0) {
          // map project ID to project object
          this.userProjectsMap = new Map(userProjectList.map(userProject => [userProject.pid, userProject]));

          // calculate whether project colors are light or dark
          const projectColorBrightnessMap: Map<number, boolean> = new Map();
          userProjectList.forEach(userProject => {
            if (userProject.color != null) {
              projectColorBrightnessMap.set(userProject.pid, this.userProjectService.isLightColor(userProject.color));
            }

            // get single project information
            if (userProject.pid == this.pid) {
              this.name = userProject.name;
              this.ownerID = userProject.ownerID;
              this.creationTime = userProject.creationTime;
              if (userProject.color != null) {
                this.color = userProject.color;
                this.inputColor = "#" + userProject.color;
                this.colorIsBright = this.userProjectService.isLightColor(userProject.color);
              }
            }
          });
          this.colorBrightnessMap = projectColorBrightnessMap;
          this.projectDataIsLoaded = true;
        }
      });
  }

  public getUserProjectFilesArray(): ReadonlyArray<DashboardUserFileEntry> {
    const fileArray = this.userProjectService.getProjectFiles();
    if (!fileArray) {
      return [];
    }
    return fileArray;
  }

  /**
   * navigate to another project page
   */
  public jumpToProject({ pid }: UserProject): void {
    this.router.navigate([`${ROUTER_USER_PROJECT_BASE_URL}/${pid}`]).then(null);
  }

  /**
   * opens and closes color picker
   */
  public toggleColorPicker() {
    this.colorPickerIsSelected = !this.colorPickerIsSelected;
  }

  public updateProjectColor(color: string) {
    color = color.substring(1);
    this.colorPickerIsSelected = false;

    if (this.userProjectService.isInvalidColorFormat(color)) {
      this.notificationService.error("Cannot update project color. Color must be in valid HEX format");
      return;
    }

    if (this.color === color) {
      return;
    }

    this.userProjectService
      .updateProjectColor(this.pid, color)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: () => {
          this.color = color;
          this.colorIsBright = this.userProjectService.isLightColor(this.color);
          this.updateProjectStatus = "updated project color"; // cause workflow / file components to update project filtering list
        },
        error: (err: unknown) => {
          // @ts-ignore
          this.notificationService.error(err.error.message);
        },
      });
  }

  public removeProjectColor() {
    this.colorPickerIsSelected = false;

    if (this.color == null) {
      this.notificationService.error("There is no color to delete for this project");
      return;
    }

    this.userProjectService
      .deleteProjectColor(this.pid)
      .pipe(untilDestroyed(this))
      .subscribe(_ => {
        this.color = null;
        this.inputColor = "#ffffff";
        this.updateProjectStatus = "removed project color"; // cause workflow / file components to update project filtering list
      });
  }

  // TODO: will be removed in future refactor to reuse UserFileSection component
  public removeFileFromProject(pid: number, fid: number): void {
    this.userProjectService
      .removeFileFromProject(pid, fid)
      .pipe(untilDestroyed(this))
      .subscribe(() => {});
  }

  // ----------------- for file card
  public addFileSizeUnit(fileSize: number): string {
    return this.userFileService.addFileSizeUnit(fileSize);
  }

  public confirmEditFileName(dashboardUserFileEntry: DashboardUserFileEntry, name: string, index: number): void {
    const {
      file: { fid },
    } = dashboardUserFileEntry;
    this.userFileService
      .updateFileName(fid, name)
      .pipe(untilDestroyed(this))
      .subscribe(
        () => {
          this.userProjectService.refreshFilesOfProject(this.pid); // -- perform appropriate call for project page
        },
        (err: unknown) => {
          // @ts-ignore // TODO: fix this with notification component
          this.notificationService.error(err.error.message);
          this.userProjectService.refreshFilesOfProject(this.pid); // -- perform appropriate call for project page
        }
      )
      .add(() => (this.isEditingFileName = this.isEditingFileName.filter(fileIsEditing => fileIsEditing != index)));
  }

  public onClickOpenFileShareAccess(dashboardUserFileEntry: DashboardUserFileEntry): void {
    const modalRef = this.modalService.open(NgbdModalUserFileShareAccessComponent);
    modalRef.componentInstance.dashboardUserFileEntry = dashboardUserFileEntry;
  }

  public downloadUserFile(userFileEntry: DashboardUserFileEntry): void {
    this.userFileService
      .downloadUserFile(userFileEntry.file)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: (response: Blob) => {
          // prepare the data to be downloaded.
          const dataType = response.type;
          const binaryData = [];
          binaryData.push(response);

          // create a download link and trigger it.
          const downloadLink = document.createElement("a");
          downloadLink.href = URL.createObjectURL(new Blob(binaryData, { type: dataType }));
          downloadLink.setAttribute("download", userFileEntry.file.name);
          document.body.appendChild(downloadLink);
          downloadLink.click();
          URL.revokeObjectURL(downloadLink.href);
        },
        error: (err: unknown) => {
          // TODO: fix this with notification component
          this.notificationService.error((err as any).error.message);
        },
      });
  }

  /**
   * Created new implementation in project service to
   * ensure files in the project page are refreshed
   *
   * @param userFileEntry
   */
  public deleteUserFileEntry(userFileEntry: DashboardUserFileEntry): void {
    this.userProjectService.deleteDashboardUserFileEntry(this.pid, userFileEntry);
  }
}
