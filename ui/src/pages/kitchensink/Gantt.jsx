import React, { Component } from "react";
import Timeline from "react-vis-timeline-2";
import moment from "moment";
import { Paper } from "../../components";

function createItem(id, startTime) {
  return {
    id: id,
    group: id,
    content: "item " + id,
    start: startTime,
    end: startTime.clone().add(1, "minute"),
  };
}

const initialGroups = [],
  initialItems = [];
const now = moment().minutes(0).seconds(0).milliseconds(0);
const itemCount = 20;
for (let i = 0; i < itemCount; i++) {
  const start = now.clone().add(Math.random() * 200, "minutes");
  initialGroups.push({ id: i, content: "group " + i });
  initialItems.push(createItem(i, start));
}

export default class Gantt extends Component {
  timelineRef = React.createRef();

  constructor(props) {
    super(props);

    this.state = {
      selectedIds: [],
    };
  }

  /*
	onAddItem = () => {
		var nextId = this.timelineRef.current.items.length + 1;
		const group = Math.floor(Math.random() * groupCount);
		this.timelineRef.current.items.add(createItem(nextId, group, names[group], moment()));
		this.timelineRef.current.timeline.fit();
	};
  */

  onFit = () => {
    this.timelineRef.current.timeline.fit();
  };

  render() {
    return (
      <Paper style={{ padding: 15 }}>
        <p className="header">This example demonstrate using groups.</p>
        <button onClick={this.onAddItem}>Add Item</button>
        <button onClick={this.onFit}>Fit Screen</button>
        <div className="timeline-container">
          <Timeline
            ref={this.timelineRef}
            clickHandler={this.clickHandler}
            selection={this.state.selectedIds}
            initialGroups={initialGroups}
            initialItems={initialItems}
            options={{
              orientation: "top",
              zoomKey: "ctrlKey",
              type: "range",
            }}
          />
        </div>
        <br />
      </Paper>
    );
  }

  clickHandler = () => {
    const { group } = this.props;
    var items = this.timelineRef.current.items.get();
    const selectedIds = items
      .filter((item) => item.group === group)
      .map((item) => item.id);
    this.setState({
      selectedIds,
    });
  };
}
