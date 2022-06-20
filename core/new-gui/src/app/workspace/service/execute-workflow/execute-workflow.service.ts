import { Injectable } from "@angular/core";
import { Observable, Subject } from "rxjs";
import { WorkflowActionService } from "../workflow-graph/model/workflow-action.service";
import { WorkflowGraphReadonly } from "../workflow-graph/model/workflow-graph";
import {
  BreakpointInfo,
  ExecutionState,
  ExecutionStateInfo,
  LogicalLink,
  LogicalOperator,
  LogicalPlan,
} from "../../types/execute-workflow.interface";
import { environment } from "../../../../environments/environment";
import { WorkflowWebsocketService } from "../workflow-websocket/workflow-websocket.service";
import { Breakpoint, BreakpointRequest, BreakpointTriggerInfo } from "../../types/workflow-common.interface";
import { OperatorCurrentTuples, TexeraWebsocketEvent } from "../../types/workflow-websocket.interface";
import { isEqual } from "lodash-es";
import { PAGINATION_INFO_STORAGE_KEY, ResultPaginationInfo } from "../../types/result-table.interface";
import { sessionGetObject, sessionSetObject } from "../../../common/util/storage";
import { WorkflowCollabService } from "../workflow-collab/workflow-collab.service";

// TODO: change this declaration
export const FORM_DEBOUNCE_TIME_MS = 150;

export const EXECUTE_WORKFLOW_ENDPOINT = "queryplan/execute";

export const PAUSE_WORKFLOW_ENDPOINT = "pause";
export const RESUME_WORKFLOW_ENDPOINT = "resume";

export const EXECUTION_TIMEOUT = 3000;

/**
 * ExecuteWorkflowService sends the current workflow data to the backend
 *  for execution, then receives backend's response and broadcast it to other components.
 *
 * ExecuteWorkflowService transforms the frontend workflow graph
 *  into backend API compatible workflow graph before sending the request.
 *
 * Components should call executeWorkflow() function to execute the current workflow
 *
 * Components and Services should subscribe to getExecuteStartedStream()
 *  in order to capture the event of workflow graph starts executing.
 *
 * Components and Services subscribe to getExecuteEndedStream()
 *  for the event of the execution result (or errro) returned by the backend.
 *
 * @author Zuozhi Wang
 * @author Henry Chen
 */
@Injectable({
  providedIn: "root",
})
export class ExecuteWorkflowService {
  private currentState: ExecutionStateInfo = {
    state: ExecutionState.Uninitialized,
  };
  private executionStateStream = new Subject<{
    previous: ExecutionStateInfo;
    current: ExecutionStateInfo;
  }>();

  private executionTimeoutID: number | undefined;
  private clearTimeoutState: ExecutionState[] | undefined;

  constructor(
    private workflowActionService: WorkflowActionService,
    private workflowWebsocketService: WorkflowWebsocketService,
    private workflowCollabService: WorkflowCollabService
  ) {
    if (environment.amberEngineEnabled) {
      workflowWebsocketService.websocketEvent().subscribe(event => {
        // workflow status related event
        const newState = this.handleExecutionEvent(event);
        if (newState !== undefined) {
          this.updateExecutionState(newState);
        }
      });
    }
  }

  public handleExecutionEvent(event: TexeraWebsocketEvent): ExecutionStateInfo | undefined {
    switch (event.type) {
      case "WorkflowStateEvent":
        let newState = ExecutionState[event.state];
        switch (newState) {
          case ExecutionState.Paused:
            if (
              this.currentState.state === ExecutionState.BreakpointTriggered ||
              this.currentState.state === ExecutionState.Paused
            ) {
              return this.currentState;
            } else {
              return { state: ExecutionState.Paused, currentTuples: {} };
            }
          case ExecutionState.Aborted:
          case ExecutionState.BreakpointTriggered:
            // for these 2 states, backend will send an additional message after this status event.
            return undefined;
          default:
            return { state: newState };
        }
      case "RecoveryStartedEvent":
        return { state: ExecutionState.Recovering };
      case "OperatorCurrentTuplesUpdateEvent":
        if (this.currentState.state === ExecutionState.BreakpointTriggered) {
          return this.currentState;
        }
        let pausedCurrentTuples: Readonly<Record<string, OperatorCurrentTuples>>;
        if (this.currentState.state === ExecutionState.Paused) {
          pausedCurrentTuples = this.currentState.currentTuples;
        } else {
          pausedCurrentTuples = {};
        }
        const currentTupleUpdate: Record<string, OperatorCurrentTuples> = {};
        currentTupleUpdate[event.operatorID] = event;
        const newCurrentTuples: Record<string, OperatorCurrentTuples> = {
          ...currentTupleUpdate,
          ...pausedCurrentTuples,
        };
        return {
          state: ExecutionState.Paused,
          currentTuples: newCurrentTuples,
        };
      case "BreakpointTriggeredEvent":
        return { state: ExecutionState.BreakpointTriggered, breakpoint: event };
      case "WorkflowErrorEvent":
        const errorMessages: Record<string, string> = {};
        Object.entries(event.operatorErrors).forEach(entry => {
          errorMessages[entry[0]] = `${entry[1].propertyPath}: ${entry[1].message}`;
        });
        Object.entries(event.generalErrors).forEach(entry => {
          errorMessages[entry[0]] = entry[1];
        });
        return { state: ExecutionState.Aborted, errorMessages: errorMessages };
      // TODO: Merge WorkflowErrorEvent and ErrorEvent
      case "WorkflowExecutionErrorEvent":
        return {
          state: ExecutionState.Aborted,
          errorMessages: { WorkflowExecutionError: event.message },
        };
      default:
        return undefined;
    }
  }

  public getExecutionState(): ExecutionStateInfo {
    return this.currentState;
  }

  public getErrorMessages(): Readonly<Record<string, string>> | undefined {
    if (this.currentState?.state === ExecutionState.Aborted) {
      return this.currentState.errorMessages;
    }
    return undefined;
  }

  public getBreakpointTriggerInfo(): BreakpointTriggerInfo | undefined {
    if (this.currentState?.state === ExecutionState.BreakpointTriggered) {
      return this.currentState.breakpoint;
    }
    return undefined;
  }

  public executeWorkflow(): void {
    if (environment.amberEngineEnabled) {
      this.executeWorkflowAmberTexera();
    } else {
      throw new Error("old texera engine not supported");
    }
  }

  public executeWorkflowAmberTexera(): void {
    // get the current workflow graph
    const logicalPlan = ExecuteWorkflowService.getLogicalPlanRequest(this.workflowActionService.getTexeraGraph());
    console.log(logicalPlan);
    // wait for the form debounce to complete, then send
    window.setTimeout(() => {
      this.workflowWebsocketService.send("WorkflowExecuteRequest", logicalPlan);
    }, FORM_DEBOUNCE_TIME_MS);
    this.setExecutionTimeout(
      "submit workflow timeout",
      ExecutionState.Initializing,
      ExecutionState.Running,
      ExecutionState.Aborted
    );

    // add flag for new execution of workflow
    // so when next time the result panel is displayed, it will use new data
    // instead of those stored in the session storage
    const resultPaginationInfo = sessionGetObject<ResultPaginationInfo>(PAGINATION_INFO_STORAGE_KEY);
    if (resultPaginationInfo) {
      sessionSetObject(PAGINATION_INFO_STORAGE_KEY, {
        ...resultPaginationInfo,
        newWorkflowExecuted: true,
      });
    }
  }

  public pauseWorkflow(): void {
    if (!environment.pauseResumeEnabled || !environment.amberEngineEnabled) {
      return;
    }
    if (this.currentState === undefined || this.currentState.state !== ExecutionState.Running) {
      throw new Error("cannot pause workflow, the current execution state is " + this.currentState?.state);
    }
    this.workflowWebsocketService.send("WorkflowPauseRequest", {});
    this.setExecutionTimeout("pause operation timeout", ExecutionState.Paused, ExecutionState.Aborted);
  }

  public killWorkflow(): void {
    if (!environment.pauseResumeEnabled || !environment.amberEngineEnabled) {
      return;
    }
    if (
      this.currentState.state === ExecutionState.Uninitialized ||
      this.currentState.state === ExecutionState.Completed
    ) {
      throw new Error("cannot kill workflow, the current execution state is " + this.currentState.state);
    }
    this.workflowWebsocketService.send("WorkflowKillRequest", {});
  }

  public resumeWorkflow(): void {
    if (!environment.pauseResumeEnabled || !environment.amberEngineEnabled) {
      return;
    }
    if (
      !(
        this.currentState.state === ExecutionState.Paused ||
        this.currentState.state === ExecutionState.BreakpointTriggered
      )
    ) {
      throw new Error("cannot resume workflow, the current execution state is " + this.currentState.state);
    }
    this.workflowWebsocketService.send("WorkflowResumeRequest", {});
    this.setExecutionTimeout("resume operation timeout", ExecutionState.Running, ExecutionState.Aborted);
  }

  public addBreakpointRuntime(linkID: string, breakpointData: Breakpoint): void {
    if (!environment.amberEngineEnabled) {
      return;
    }
    if (
      this.currentState.state !== ExecutionState.BreakpointTriggered &&
      this.currentState.state !== ExecutionState.Paused
    ) {
      throw new Error("cannot add breakpoint at runtime, the current execution state is " + this.currentState.state);
    }
    console.log("sending add breakpoint request");
    this.workflowWebsocketService.send(
      "AddBreakpointRequest",
      ExecuteWorkflowService.transformBreakpoint(this.workflowActionService.getTexeraGraph(), linkID, breakpointData)
    );
  }

  public skipTuples(): void {
    if (!environment.amberEngineEnabled) {
      return;
    }
    if (this.currentState.state !== ExecutionState.BreakpointTriggered) {
      throw new Error("cannot skip tuples, the current execution state is " + this.currentState.state);
    }
    this.currentState.breakpoint.report.forEach(fault => {
      this.workflowWebsocketService.send("SkipTupleRequest", {
        faultedTuple: fault.faultedTuple,
        actorPath: fault.actorPath,
      });
    });
  }

  public retryExecution(): void {
    if (!environment.amberEngineEnabled) {
      return;
    }
    if (this.currentState.state !== ExecutionState.BreakpointTriggered) {
      throw new Error("cannot retry the current tuple, the current execution state is " + this.currentState.state);
    }
    this.workflowWebsocketService.send("RetryRequest", {});
  }

  public modifyOperatorLogic(operatorID: string): void {
    if (!environment.amberEngineEnabled) {
      return;
    }
    if (
      this.currentState.state !== ExecutionState.BreakpointTriggered &&
      this.currentState.state !== ExecutionState.Paused
    ) {
      throw new Error("cannot modify logic, the current execution state is " + this.currentState.state);
    }
    const op = this.workflowActionService.getTexeraGraph().getOperator(operatorID);
    const operator: LogicalOperator = {
      ...op.operatorProperties,
      operatorID: op.operatorID,
      operatorType: op.operatorType,
    };
    this.workflowWebsocketService.send("ModifyLogicRequest", { operator });
  }

  public getExecutionStateStream(): Observable<{
    previous: ExecutionStateInfo;
    current: ExecutionStateInfo;
  }> {
    return this.executionStateStream.asObservable();
  }

  public resetExecutionState(): void {
    this.currentState = {
      state: ExecutionState.Uninitialized,
    };
  }

  private setExecutionTimeout(message: string, ...clearTimeoutState: ExecutionState[]) {
    if (this.executionTimeoutID !== undefined) {
      this.clearExecutionTimeout();
    }
    this.executionTimeoutID = window.setTimeout(() => {
      this.updateExecutionState({
        state: ExecutionState.Aborted,
        errorMessages: { timeout: message },
      });
    }, EXECUTION_TIMEOUT);
    this.clearTimeoutState = clearTimeoutState;
  }

  private clearExecutionTimeout() {
    if (this.executionTimeoutID !== undefined) {
      window.clearTimeout(this.executionTimeoutID);
      this.executionTimeoutID = undefined;
      this.clearTimeoutState = undefined;
    }
  }

  private updateExecutionState(stateInfo: ExecutionStateInfo): void {
    if (isEqual(this.currentState, stateInfo)) {
      return;
    }
    this.updateWorkflowActionLock(stateInfo);
    if (this.clearTimeoutState?.includes(stateInfo.state)) {
      this.clearExecutionTimeout();
    }
    const previousState = this.currentState;
    // update current state
    this.currentState = stateInfo;
    // emit event
    this.executionStateStream.next({
      previous: previousState,
      current: this.currentState,
    });
  }

  /**
   * enables or disables workflow action service based on execution state
   */
  private updateWorkflowActionLock(stateInfo: ExecutionStateInfo): void {
    switch (stateInfo.state) {
      case ExecutionState.Completed:
      case ExecutionState.Aborted:
      case ExecutionState.Uninitialized:
      case ExecutionState.BreakpointTriggered:
        this.workflowActionService.toggleLockListen(true);
        if (this.workflowCollabService.isLockGranted()) {
          this.workflowActionService.enableWorkflowModification();
        }
        return;
      case ExecutionState.Paused:
      case ExecutionState.Pausing:
      case ExecutionState.Recovering:
      case ExecutionState.Resuming:
      case ExecutionState.Running:
      case ExecutionState.Initializing:
        this.workflowActionService.toggleLockListen(false);
        this.workflowActionService.disableWorkflowModification();
        return;
      default:
        return;
    }
  }

  /**
   * Transform a workflowGraph object to the HTTP request body according to the backend API.
   *
   * All the operators in the workflowGraph will be transformed to LogicalOperator objects,
   *  where each operator has an operatorID and operatorType along with
   *  the properties of the operator.
   *
   * All the links in the workflowGraph will be transformed to LogicalLink objects,
   *  where each link will store its source id as its origin and target id as its destination.
   *
   * @param workflowGraph
   */
  public static getLogicalPlanRequest(workflowGraph: WorkflowGraphReadonly): LogicalPlan {
    const getInputPortOrdinal = (operatorID: string, inputPortID: string): number => {
      return workflowGraph.getOperator(operatorID).inputPorts.findIndex(port => port.portID === inputPortID);
    };
    const getInputPortName = (operatorID: string, inputPortID: string): string => {
      return (
        workflowGraph.getOperator(operatorID).inputPorts[getInputPortOrdinal(operatorID, inputPortID)].displayName ?? ""
      );
    };
    const getOutputPortOrdinal = (operatorID: string, outputPortID: string): number => {
      return workflowGraph.getOperator(operatorID).outputPorts.findIndex(port => port.portID === outputPortID);
    };
    const getOutputPortName = (operatorID: string, outputPortID: string): string => {
      return (
        workflowGraph.getOperator(operatorID).outputPorts[getOutputPortOrdinal(operatorID, outputPortID)].displayName ??
        ""
      );
    };

    const operators: LogicalOperator[] = workflowGraph.getAllEnabledOperators().map(op => ({
      ...op.operatorProperties,
      operatorID: op.operatorID,
      operatorType: op.operatorType,
    }));

    const links: LogicalLink[] = workflowGraph.getAllEnabledLinks().map(link => ({
      origin: {
        operatorID: link.source.operatorID,
        portOrdinal: getOutputPortOrdinal(link.source.operatorID, link.source.portID),
        portName: getOutputPortName(link.source.operatorID, link.source.portID),
      },
      destination: {
        operatorID: link.target.operatorID,
        portOrdinal: getInputPortOrdinal(link.target.operatorID, link.target.portID),
        portName: getInputPortName(link.target.operatorID, link.target.portID),
      },
    }));

    const breakpoints: BreakpointInfo[] = Array.from(workflowGraph.getAllEnabledLinkBreakpoints().entries()).map(e =>
      ExecuteWorkflowService.transformBreakpoint(workflowGraph, e[0], e[1])
    );

    const cachedOperatorIds: string[] = Array.from(workflowGraph.getCachedOperators()).filter(
      op => !workflowGraph.isOperatorDisabled(op)
    );

    return { operators, links, breakpoints, cachedOperatorIds };
  }

  public static transformBreakpoint(
    workflowGraph: WorkflowGraphReadonly,
    linkID: string,
    breakpointData: Breakpoint
  ): BreakpointInfo {
    const operatorID = workflowGraph.getLinkWithID(linkID).source.operatorID;
    let breakpoint: BreakpointRequest;
    if ("condition" in breakpointData) {
      breakpoint = { ...breakpointData, type: "ConditionBreakpoint" };
    } else if ("count" in breakpointData) {
      breakpoint = { ...breakpointData, type: "CountBreakpoint" };
    } else {
      throw new Error("unhandled breakpoint data " + breakpointData);
    }
    return { operatorID, breakpoint };
  }
}
