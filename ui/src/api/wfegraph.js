import clone from "lodash/fp/clone";
import isEmpty from "lodash/fp/isEmpty";

class Workflow2Graph {

  constructor() {
  }

  convert(wfe, meta) {
    this._convert(wfe, meta);
    return {edges: this.edges, vertices: this.vertices, id: wfe.workflowId};
  }
  _convert(wfe = {}, meta = {}) {
    const subworkflows = {};
    const metaTasks = meta.tasks && clone(meta.tasks) || [];
    metaTasks.push({type:'final', name:'final', label: '', taskReferenceName: 'final', system: true});
    metaTasks.unshift({type:'start', name:'start', label: '', taskReferenceName: 'start', system: true});

    const forks = [];
    const tasks = wfe.tasks || [];
    this.executedTasks = {};
    tasks.forEach(tt=>{
      this.executedTasks[tt.referenceTaskName] = {
        status: tt.status,
        input: tt.inputData,
        output: tt.outputData,
        taskType: tt.taskType,
        reasonForIncompletion:
        tt.reasonForIncompletion,
        task: tt
      };
      if(tt.taskType === 'FORK'){
        let wfts = [];
        let forkedTasks = tt.inputData && tt.inputData.forkedTasks || [];
        forkedTasks.forEach(ft =>{
          wfts.push({name: ft, referenceTaskName: ft, type: 'SIMPLE'});
        });
        forks[tt.referenceTaskName] = wfts;
      }

    }, this);

    let nodes = [];
    let vertices = {};
    wfe.tasks.forEach(t => {
      // remove __N suffix because DO_WHILE has this suffix in referenceTaskName
      this.executedTasks[t.referenceTaskName.split('__')[0]] = {status: t.status, input: t.inputData, output: t.outputData, taskType: t.taskType, reasonForIncompletion: t.reasonForIncompletion, task: t};
    })
    // Go through each JOIN in the workflow and build up a mapping to their joinOn data
    this.joinOnTaskMapping = {};
    metaTasks.forEach(t => {
      if(t.type === 'JOIN' && !isEmpty(t.joinOn)) {
        t.joinOn.forEach(jot => {
          let allJots = this.joinOnTaskMapping[jot];
          if (isEmpty(allJots)) {
            allJots = new Set();
          }
          allJots.add(t.taskReferenceName);
          this.joinOnTaskMapping[jot] = allJots;
        });
      }
    }, this);

    this.executedTasks['final'] = {status: '', input: '', output: wfe.output, taskType: 'final', reasonForIncompletion: wfe.reasonForIncompletion, task: {}};
    this.executedTasks['start'] = {status: 'STARTED', input: wfe.input, output: '', taskType: 'final', reasonForIncompletion: '', task: {}};
    this.getTaskNodes(vertices, nodes, metaTasks, forks, subworkflows, true);

    this.edges = nodes;
    this.vertices = vertices;
    this.vertices['final'] = {name: 'final', ref: 'final', type: 'final', style: 'fill:#ffffff', shape: 'circle', system: true};
    this.vertices['start'] = {name: 'start', ref: 'start', type: 'start', style: 'fill: #ffffff', shape: 'circle', system: true};

    for(let v in this.vertices) {

      let et = this.executedTasks[v];
      let status = et ? et.status : '';

      let style = '';
      let labelStyle = '';
      switch (status) {
        case 'FAILED':
        case 'TIMED_OUT':
        case 'CANCELLED':
        case 'CANCELED':
        case 'FAILED_WITH_TERMINAL_ERROR':
          style = 'stroke: #ff0000; fill: #ff0000';
          labelStyle = 'fill:#ffffff; stroke-width: 1px';
          break;
        case 'IN_PROGRESS':
        case 'SCHEDULED':
          style = 'stroke: orange; fill: orange';
          labelStyle = 'fill:#ffffff; stroke-width: 1px';
          break;
        case 'COMPLETED':
          style = 'stroke: #48a770; fill: #48a770';
          labelStyle = 'fill:#ffffff; stroke-width: 1px';
          break;
        case 'COMPLETED_WITH_ERRORS':
          style = 'stroke: #FF8C00; fill: #FF8C00';
          labelStyle = 'fill:#ffffff; stroke-width: 1px';
          break;
        case 'SKIPPED':
          style = 'stroke: #cccccc; fill: #ccc';
          labelStyle = 'fill:#ffffff; stroke-width: 1px';
          break;
        case '':
          break;
        default:
          break;
      }
      if (status != '') {

        this.vertices[v].style = style;
        this.vertices[v].labelStyle = labelStyle;
        let tooltip = '<p><strong>Input</strong></p>' + JSON.stringify(et.input, null, 2) + '<p></p><p><strong>Output</strong></p>' + JSON.stringify(et.output, null, 2);
        tooltip += '<p></p><p><strong>Status</strong> : ' + et.status + '</p>';
        if (status == 'FAILED') {
          tooltip += '<p><strong>Failure Reason</strong></p><p>' + et.reasonForIncompletion + '</p>';
        }
        this.vertices[v].data = et;
        this.vertices[v].tooltip = tooltip;
        this.vertices[v].tooltipTitle = et.taskType + ' - ' + et.status;
      }
    }

  }

  getTaskNodes(vertices, nodes, tasks, forks, subworkflows, isExecutingCase){
    if(tasks == null || tasks.length == null){
      return nodes;
    }
    for(let i = 1; i < tasks.length; i++){
      this.getNodes(vertices, nodes, tasks[i-1], tasks[i], forks, subworkflows, isExecutingCase);
    }
    return nodes;
  }

  getNodes(vertices, nodes, t1, t2, forks, subworkflows, isExecutingCase){

    let executed = 'stroke: #000000; fill: transparent';
    let defstyle = 'stroke: #ccc; fill: transparent; stroke-dasharray: 5, 5';
    let isExecuting = isExecutingCase;
    if(t1.type == 'final' && t1.type == t2.type){
      vertices[t1.taskReferenceName] = {name: t1.name, ref: t1.taskReferenceName, type: 'simple', style: '', shape: 'circle', system: true, description: t1.description};
      return nodes;
    }

    if(t1.type == 'FORK_JOIN'){

      vertices[t1.taskReferenceName] = {name: 'FORK', ref: t1.taskReferenceName, type: 'simple', style: 'fill: #ff0', shape: 'house', system: true, description: t1.description};
      let r = t1.taskReferenceName;

      let fork = t1.forkTasks || [];
      fork.forEach(ft => {

        let tasks = ft;

        vertices[tasks[0].taskReferenceName] = {name: tasks[0].name, ref: tasks[0].taskReferenceName, type: tasks[0].type, style: '', shape: 'rect', description: tasks[0].description};

        let style = defstyle;
        if(this.executedTasks[tasks[0].taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
          style = executed;
        } else {
          isExecuting = false;
        }

        nodes.push({type: 'FORK', from: t1.taskReferenceName, to: tasks[0].taskReferenceName, label: '', style: style});

        this.getTaskNodes(vertices, nodes, tasks, forks, subworkflows);
        this.getNodes(vertices, nodes, tasks[tasks.length-1], t2, forks, subworkflows);
      });


    } else if(t1.type == 'FORK_JOIN_DYNAMIC'){

        vertices[t1.taskReferenceName] = {name: 'DYNAMIC_FORK', ref: t1.taskReferenceName, type: 'simple', style: 'fill: #ff0', shape: 'house', system: true, description: t1.description};
        let style = defstyle;
        if(this.executedTasks[t2.taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
          style = executed;
        } else {
          isExecuting = false;
        }
        let fts = forks[t1.taskReferenceName] || [];
        fts.forEach(ft=>{
          vertices[ft.referenceTaskName] = {name: ft.name, ref: ft.referenceTaskName, type: 'simple', style: 'fill: #ff0', shape: 'rect', description: ft.description};
          nodes.push({type: 'simple', from: t1.taskReferenceName, to: ft.referenceTaskName, label: '', style: style});
          nodes.push({type: 'simple', from: ft.referenceTaskName, to: t2.taskReferenceName, label: '', style: style});
        });
        if(fts.length == 0){
          nodes.push({type: 'simple', from: t1.taskReferenceName, to: t2.taskReferenceName, label: '', style: style});
        }


    } else if(t1.type == 'DECISION'){
      let caseExecuted = false;
      for (let k in t1.decisionCases){
          let tasks = t1.decisionCases[k];

          vertices[t1.taskReferenceName] = {name: t1.name, ref: t1.taskReferenceName, type: t1.type, style: 'fill: #ff0', shape: 'diamond', system: true, description: t1.description};
          vertices[tasks[0].taskReferenceName] = {name: tasks[0].name, ref: tasks[0].taskReferenceName, type: tasks[0].type, style: '', shape: 'rect', description: tasks[0].description};

          let style = defstyle;
          if(this.executedTasks[tasks[0].taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
            style = executed;
            caseExecuted = true;
          }

          nodes.push({type: 'decision', from: t1.taskReferenceName, to: tasks[0].taskReferenceName, label: k, style: style});
          this.getTaskNodes(vertices, nodes, tasks, forks, subworkflows, isExecuting);
          this.getNodes(vertices, nodes, tasks[tasks.length-1], t2, forks, subworkflows, isExecuting);

      }

      let tasks = t1.defaultCase;
      if(tasks == null) tasks = [];
      if(tasks.length > 0) {
          vertices[t1.taskReferenceName] = {name: t1.name, ref: t1.taskReferenceName, type: t1.type, style: 'fill: #ff0', shape: 'diamond', system: true, description: t1.description};
          vertices[tasks[0].taskReferenceName] = {name: tasks[0].name, ref: tasks[0].taskReferenceName, type: tasks[0].type, style: '', shape: 'rect', description: tasks[0].description};

          let style = defstyle;
          if(this.executedTasks[tasks[0].taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
            style = executed;
          }

          nodes.push({type: 'decision', from: t1.taskReferenceName, to: tasks[0].taskReferenceName, label: 'default', style: style});
          this.getTaskNodes(vertices, nodes, tasks, forks, subworkflows, isExecuting);
          this.getNodes(vertices, nodes, tasks[tasks.length - 1], t2, forks, subworkflows, isExecuting);
      } else {
        nodes.push({type: 'decision', from: t1.taskReferenceName, to: t2.taskReferenceName, label: "", style: !caseExecuted && isExecuting ? executed : defstyle});
      }

    } else if(t1.type == 'JOIN') {

      vertices[t1.taskReferenceName] = {name: t1.name, ref: t1.taskReferenceName, type: 'simple', style: 'fill:#ff0', shape: 'ellipse', system:true, description: t1.description};

      let style = defstyle;
      if(this.executedTasks[t2.taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
        style = executed;
      } else {
        isExecuting = false;
      }
      nodes.push({type: 'simple', from: t1.taskReferenceName, to: t2.taskReferenceName, label: '', style: style});

    } else if(t1.type == 'EVENT') {

      vertices[t1.taskReferenceName] = {name: t1.name, ref: t1.taskReferenceName, type: 'simple', style: 'fill:#ff0', shape: 'star', system:true, description: t1.description};

      let style = defstyle;
      if(this.executedTasks[t2.taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
        style = executed;
      } else {
        isExecuting = false;
      }
      nodes.push({type: 'simple', from: t1.taskReferenceName, to: t2.taskReferenceName, label: '', style: style});

    } else if(t1.type == 'SUB_WORKFLOW') {
          vertices[t1.taskReferenceName] = {name: t1.name, ref: t1.taskReferenceName, type: 'simple', style: 'fill:#efefef', shape: 'rect', system:true, description: t1.description};

        let style = defstyle;
        if(this.executedTasks[t2.taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
          style = executed;
        } else {
          isExecuting = false;
        }
        nodes.push({type: 'simple', from: t1.taskReferenceName, to: t2.taskReferenceName, label: '', style: style});

    } else if(t1.type == 'EXCLUSIVE_JOIN') {

        vertices[t1.taskReferenceName] = {name: t1.name, ref: t1.taskReferenceName, type: 'simple', style: 'fill:#ff0', shape: 'star', system:true, description: t1.description};

        let style = defstyle;
        if(this.executedTasks[t2.taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
          style = executed;
        } else {
          isExecuting = false;
        }
        nodes.push({type: 'simple', from: t1.taskReferenceName, to: t2.taskReferenceName, label: '', style: style});

      } else if(t1.type == 'DO_WHILE'){

      // vertice for DO_WHILE task
      vertices[t1.taskReferenceName] = {name: 'DO_WHILE', ref: t1.taskReferenceName, type: 'simple', style: 'fill: #ff0', shape: 'circle', system: true, description: t1.description};

      let tasks = t1.loopOver || [];

      // vertice for the first task in DO_WHILE
      vertices[tasks[0].taskReferenceName] = {name: tasks[0].name, ref: tasks[0].taskReferenceName, type: tasks[0].type, style: '', shape: 'rect', description: tasks[0].description};

      let style = defstyle;
      if(this.executedTasks[tasks[0].taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
        style = executed;
      } else {
        isExecuting = false;
      }

      // edge from DO_WHILE task to the first task in DO_WHILE
      nodes.push({type: 'simple', from: t1.taskReferenceName, to: tasks[0].taskReferenceName, label: '', style: style});

      // go into DO_WHILE inner tasks
      this.getTaskNodes(vertices, nodes, tasks, forks, subworkflows);
      // vertice for the last task in DO_WHILE, and edge from the last task in DO_WHILE to DO_WHILE
      this.getNodes(vertices, nodes, tasks[tasks.length-1], t1, forks, subworkflows);

      style = defstyle;
      if(this.executedTasks[tasks[tasks.length-1].taskReferenceName] != null && this.executedTasks[t2.taskReferenceName] != null){
        style = executed;
      } else {
        isExecuting = false;
      }

      // edge from the last task in DO_WHILE to t2
      nodes.push({type: 'simple', from: tasks[tasks.length-1].taskReferenceName, to: t2.taskReferenceName, label: '', style: style});

    } else {
        vertices[t1.taskReferenceName] = {name: t1.name, ref: t1.taskReferenceName, type: 'simple', style: '', shape: 'rect', description: t1.description};

        let style = defstyle;
        if(this.executedTasks[t2.taskReferenceName] != null && this.executedTasks[t1.taskReferenceName] != null){
          style = executed;
        } else {
          isExecuting = false;
        }

        // See if this is referenced by a joinOn, and point the node/edge "to" those joins
        if (this.joinOnTaskMapping[t1.taskReferenceName]) {
          this.joinOnTaskMapping[t1.taskReferenceName].forEach(joinRefName => {
            nodes.push({type: 'simple', from: t1.taskReferenceName, to: joinRefName, label: '', style: style});
          }, this);
        } else {
          nodes.push({type: 'simple', from: t1.taskReferenceName, to: t2.taskReferenceName, label: '', style: style});
        }
    }
    return nodes;

  }
}

export default Workflow2Graph;
