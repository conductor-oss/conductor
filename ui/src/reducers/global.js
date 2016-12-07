const initialState = {
  appWidth: '1000px'
};

export default function global(state = initialState, action) {
  switch (action.type) {
    case 'MENU_VISIBLE':
      let width = document.body.clientWidth - 180;
      return {
        ...state,
        appWidth: width + 'px'
      };
    default:
      return state;
  }
}
