import React, {Component} from "react";

export default class Tab extends Component {
    constructor(props) {
        super(props)
    }

    render() {
        const {children, active} = this.props;

        return <div className={"tab-pane" + ((active) ? " active" : " ")}>
            {children}
        </div>;
    }
}
