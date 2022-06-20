export interface UserProject
  extends Readonly<{
    pid: number;
    name: string;
    ownerID: number;
    creationTime: number;
    color: string | null;
  }> {}
