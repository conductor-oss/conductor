import React, { Component } from "react";
import { NavLink, Paper, PrimaryButton, Heading } from "../../components";
import Wrapper from "../../components/Wrapper";
import update from "immutability-helper";
class Examples extends Component {
  constructor() {
    super();
    this.state = {
      count: 0,
      negcount: {
        nested: 32,
      },
    };
  }

  click = () => {
    this.setState((prevState) =>
      update(prevState, {
        negcount: {
          nested: {
            $set: prevState.negcount.nested - 1,
          },
        },
      })
    );
  };

  render() {
    return (
      <Wrapper>
        <Paper style={{ marginTop: 30 }} padded>
          <PrimaryButton onClick={this.click}>Hello</PrimaryButton>
          <h1>
            {this.state.count} {this.state.negcount.nested}
          </h1>

          <Heading level={3} gutterBottom>
            Flow Control
          </Heading>
          <div>
            <NavLink path="/execution/14e582f0-8186-45a9-928f-e2ece512a12a">
              Static Fork
            </NavLink>
          </div>
          <div>
            <NavLink path="">Static-Fork - wide</NavLink>
          </div>
          <div>
            <NavLink path="/execution/1f232995-9e57-4ea3-86fe-a8786ca82674">
              Dynamic Fork - Success Single
            </NavLink>
          </div>
          <div>
            <NavLink path="/execution/637364c4-31bf-4c50-8c81-c04d1dafe27f">
              Dynamic Fork - Failed/Unexecuted
            </NavLink>
          </div>
          <div>
            <NavLink path="/execution/716020d3-7190-42e2-802f-d5d7c457994c">
              Dynamic Fork - None forked
            </NavLink>
          </div>

          <div>
            <NavLink path="/execution/909f7c0f-7238-4662-907d-ad7aa6811dec">
              Decision - Success - Default
            </NavLink>
          </div>
          <div>
            <NavLink path="/execution/14e582f0-8186-45a9-928f-e2ece512a12a">
              Decision - Success - Non-default
            </NavLink>
          </div>
          <div>
            <NavLink path="/execution/16b448c4-cdef-4178-8e7f-d628b8f90c1f">
              Decision w/ Exclusive Join
            </NavLink>
          </div>
          <div>
            <NavLink path="/execution/40a2ad4a-bdc1-4f1b-9944-167c0d18a523">
              Logs
            </NavLink>
          </div>
          <div>
            <NavLink path="/execution/637364c4-31bf-4c50-8c81-c04d1dafe27f">
              Subworkflow
            </NavLink>
          </div>

          <Heading level={3} gutterBottom>
            Task Variations
          </Heading>
          <div>
            <NavLink path="/execution/05723595-adc3-4ace-999c-c7a7b604d1c6">
              Lambda
            </NavLink>
          </div>
          <div>
            <NavLink path="">HTTP</NavLink>
          </div>

          <Heading level={3} gutterBottom>
            States
          </Heading>
          <div>
            <NavLink path="">Completed</NavLink>
          </div>
          <div>
            <NavLink path="">Failed</NavLink>
          </div>
        </Paper>
      </Wrapper>
    );
  }
}

export default Examples;
