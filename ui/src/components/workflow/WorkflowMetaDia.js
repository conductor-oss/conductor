import React, {Component} from 'react';
import Grapher from '../common/Grapher'
import Workflow2Graph from '../../api/wfegraph'
import defaultTo from "lodash/fp/defaultTo"

class WorkflowMetaDia extends Component {
    constructor(props) {
        super(props);

        this.state = WorkflowMetaDia.getGraphState(props);
    }

    static getGraphState(props) {
        const wfe2graph = new Workflow2Graph();
        const subwfs = defaultTo({})(props.subworkflows);
        const wfe = defaultTo({tasks: []})(props.wfe);
        const {edges, vertices} = wfe2graph.convert(wfe, props.meta);
        const subworkflows = {};

        for (const refname in subwfs) {
          let submeta = subwfs[refname].meta;
          let subwfe = subwfs[refname].wfe;
          subworkflows[refname] = () => {
            return wfe2graph.convert(subwfe, submeta)
          };
        }

        return { edges, vertices, subworkflows };

    }

    componentWillReceiveProps(nextProps) {
        this.setState(WorkflowMetaDia.getGraphState(nextProps));
    };

    render() {
        const { edges, vertices, subworkflows } = this.state;

        return <Grapher edges={edges} vertices={vertices} layout="TD-auto" innerGraph={subworkflows}/>;
    }
}

export default WorkflowMetaDia;
