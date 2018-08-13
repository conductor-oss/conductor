import React from 'react';

const Tab = ({ children, active }) => <div className={'tab-pane' + (active ? ' active' : ' ')}>{children}</div>;

export default Tab;
