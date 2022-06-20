import { Component } from "@angular/core";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { NgbdModalFileAddComponent } from "./ngbd-modal-file-add/ngbd-modal-file-add.component";
import { UserFileService } from "../../../service/user-file/user-file.service";
import { DashboardUserFileEntry, UserFile } from "../../../type/dashboard-user-file-entry";
import { UserService } from "../../../../common/service/user/user.service";
import { NgbdModalUserFileShareAccessComponent } from "./ngbd-modal-file-share-access/ngbd-modal-user-file-share-access.component";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { NotificationService } from "../../../../common/service/notification/notification.service";
import { UserProjectService } from "../../../service/user-project/user-project.service";
import { UserProject } from "../../../type/user-project";
import Fuse from "fuse.js";

export const ROUTER_USER_PROJECT_BASE_URL = "/dashboard/user-project";

@UntilDestroy()
@Component({
  selector: "texera-user-file-section",
  templateUrl: "./user-file-section.component.html",
  styleUrls: ["./user-file-section.component.scss"],
})
export class UserFileSectionComponent {
  constructor(
    private modalService: NgbModal,
    private userProjectService: UserProjectService,
    private userFileService: UserFileService,
    private userService: UserService,
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit() {
    this.registerDashboardFileEntriesRefresh();
  }
  // variables for file editing / search
  public dashboardUserFileEntries: ReadonlyArray<DashboardUserFileEntry> = [];
  public isEditingName: number[] = [];
  public userFileSearchValue: string = "";
  public filteredFilenames: Array<string> = new Array();
  public isTyping: boolean = false;
  public fuse = new Fuse([] as ReadonlyArray<DashboardUserFileEntry>, {
    shouldSort: true,
    threshold: 0.2,
    location: 0,
    distance: 100,
    minMatchCharLength: 1,
    keys: ["file.name"],
  });

  // variables for project color tags
  public userProjectsMap: ReadonlyMap<number, UserProject> = new Map(); // maps pid to its corresponding UserProject
  public colorBrightnessMap: ReadonlyMap<number, boolean> = new Map(); // tracks whether each project's color is light or dark
  public userProjectsLoaded: boolean = false; // tracks whether all UserProject information has been loaded (ready to render project colors)

  // variables for filtering files by projects
  public userProjectsList: ReadonlyArray<UserProject> = []; // list of projects accessible by user
  public projectFilterList: number[] = []; // for filter by project mode, track which projects are selected
  public isSearchByProject: boolean = false; // track searching mode user currently selects

  public openFileAddComponent() {
    const modalRef = this.modalService.open(NgbdModalFileAddComponent);

    modalRef.dismissed.pipe(untilDestroyed(this)).subscribe(_ => {
      this.refreshDashboardFileEntries();
    });
  }

  public searchInputOnChange(value: string): void {
    this.isTyping = true;
    this.filteredFilenames = [];
    const fileArray = this.dashboardUserFileEntries;
    fileArray.forEach(fileEntry => {
      if (fileEntry.file.name.toLowerCase().indexOf(value.toLowerCase()) !== -1) {
        this.filteredFilenames.push(fileEntry.file.name);
      }
    });
  }

  public onClickOpenShareAccess(dashboardUserFileEntry: DashboardUserFileEntry): void {
    const modalRef = this.modalService.open(NgbdModalUserFileShareAccessComponent);
    modalRef.componentInstance.dashboardUserFileEntry = dashboardUserFileEntry;
  }

  public getFileArray(): ReadonlyArray<DashboardUserFileEntry> {
    const fileArray = this.dashboardUserFileEntries;
    if (!fileArray) {
      return [];
    } else if (this.userFileSearchValue !== "" && this.isTyping === false && !this.isSearchByProject) {
      this.fuse.setCollection(fileArray);
      return this.fuse.search(this.userFileSearchValue).map(item => {
        return item.item;
      });
    } else if (this.isTyping === false && this.isSearchByProject) {
      let newFileEntries = fileArray.slice();
      this.projectFilterList.forEach(
        pid => (newFileEntries = newFileEntries.filter(file => file.projectIDs.includes(pid)))
      );
      return newFileEntries;
    }
    return fileArray;
  }

  public deleteUserFileEntry(userFileEntry: DashboardUserFileEntry): void {
    this.userFileService.deleteDashboardUserFileEntry(userFileEntry).subscribe(
      () => this.refreshDashboardFileEntries(),
      (err: unknown) => {
        // @ts-ignore // TODO: fix this with notification component
        (err: unknown) => alert("Can't delete the file entry: " + err.error);
      }
    );
  }

  public disableAddButton(): boolean {
    return !this.userService.isLogin();
  }

  public addFileSizeUnit(fileSize: number): string {
    return this.userFileService.addFileSizeUnit(fileSize);
  }

  public downloadUserFile(userFileEntry: DashboardUserFileEntry): void {
    this.userFileService
      .downloadUserFile(userFileEntry.file)
      .pipe(untilDestroyed(this))
      .subscribe(
        (response: Blob) => {
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
        (err: unknown) => {
          // @ts-ignore // TODO: fix this with notification component
          this.message.error(err.error.message);
        }
      );
  }

  public confirmUpdateFileCustomName(
    dashboardUserFileEntry: DashboardUserFileEntry,
    name: string,
    index: number
  ): void {
    const {
      file: { fid },
    } = dashboardUserFileEntry;
    this.userFileService
      .updateFileName(fid, name)
      .pipe(untilDestroyed(this))
      .subscribe(
        () => this.refreshDashboardFileEntries(),
        (err: unknown) => {
          // @ts-ignore // TODO: fix this with notification component
          this.notificationService.error(err.error.message);
          this.refreshDashboardFileEntries();
        }
      )
      .add(() => (this.isEditingName = this.isEditingName.filter(fileIsEditing => fileIsEditing != index)));
  }

  public toggleSearchMode(): void {
    this.isSearchByProject = !this.isSearchByProject;

    // TODO : update local cache & switch here after refactoring for reuse in  User Projects is done
    // if (this.isSearchByProject) {
    // } else {
    // }
  }

  /**
   * navigate to individual project page
   */
  public jumpToProject({ pid }: UserProject): void {
    this.router.navigate([`${ROUTER_USER_PROJECT_BASE_URL}/${pid}`]).then(null);
  }

  public removeFileFromProject(pid: number, fid: number): void {
    this.userProjectService
      .removeFileFromProject(pid, fid)
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        this.refreshDashboardFileEntries();
      });
  }

  private registerDashboardFileEntriesRefresh(): void {
    this.userService
      .userChanged()
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        if (this.userService.isLogin()) {
          this.refreshUserProjects();
          this.refreshDashboardFileEntries();
        } else {
          this.clearDashboardFileEntries();
        }
      });
  }

  private refreshUserProjects(): void {
    this.userProjectService
      .retrieveProjectList()
      .pipe(untilDestroyed(this))
      .subscribe((userProjectList: UserProject[]) => {
        if (userProjectList != null && userProjectList.length > 0) {
          // map project ID to project object
          this.userProjectsMap = new Map(userProjectList.map(userProject => [userProject.pid, userProject]));

          // calculate whether project colors are light or dark
          const projectColorBrightnessMap: Map<number, boolean> = new Map();
          userProjectList.forEach(userProject => {
            if (userProject.color != null) {
              projectColorBrightnessMap.set(userProject.pid, this.userProjectService.isLightColor(userProject.color));
            }
          });
          this.colorBrightnessMap = projectColorBrightnessMap;

          // store all projects containing these files
          this.userProjectsList = userProjectList;
          this.userProjectsLoaded = true;
        }
      });
  }
  private refreshDashboardFileEntries(): void {
    this.userFileService
      .retrieveDashboardUserFileEntryList()
      .pipe(untilDestroyed(this))
      .subscribe(dashboardUserFileEntries => {
        this.dashboardUserFileEntries = dashboardUserFileEntries;
        this.userFileService.updateUserFilesChangedEvent();
      });
  }

  private clearDashboardFileEntries(): void {
    this.dashboardUserFileEntries = [];
    this.userFileService.updateUserFilesChangedEvent();
  }
}
