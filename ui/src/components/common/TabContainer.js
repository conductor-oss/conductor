import React, {Component} from "react";
import {Nav, NavItem} from 'react-bootstrap';
import find from "lodash/fp/find";
import map from "lodash/fp/map";
const mapWithIndex = map.convert({cap: false});

const getActiveTab = ({props: {eventKey}}) => eventKey;
const renderTab = (activeTab, onSelect) => ({props: {eventKey, title}}, i) => <NavItem key={i} eventKey={eventKey} active={activeTab === eventKey} onSelect={onSelect}>{title}</NavItem>;

export default class TabContainer extends Component {
    constructor(props) {
        super(props);
        this.state = {active: 1};
        this.changeTab = this.changeTab.bind(this);
    }

    changeTab(eventKey) {
        this.setState({active: eventKey});
    }

    render() {
        const {children} = this.props;
        const {active} = this.state;

        const tab = find(tab => getActiveTab(tab) === active)(children);
        const navItems = mapWithIndex(renderTab(active, this.changeTab))(children);

        return <div>
            <Nav bsStyle="tabs">
                {navItems}
            </Nav>
            <div className="tab-content">
                {tab && React.cloneElement(tab, {active: true})}
            </div>
        </div>
    }
}
