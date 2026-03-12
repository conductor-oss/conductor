import { MenuItemType } from "components/Sidebar/types";
import { flattenMenu, searchResultExtractor } from "./helpers";

const taskDefinitions = [
  { name: "something", description: "somthing ready" },
  { name: "eac_sca", description: "cool value" },
  { name: "najeeb_test", description: "breeze is cold" },
];

const workflowDefinitions = [
  {
    updateTime: 1692226077142,
    name: "amqp_1",
    description:
      "Edit or extend this sample workflow. Set the workflow name to get started",
    version: 1,
    failureWorkflow: "",
    schemaVersion: 2,
    restartable: true,
    workflowStatusListenerEnabled: false,
    ownerEmail: "vasiliy.pankov@orkes.io",
    timeoutSeconds: 0,
    tasks: [],
  },
  {
    updateTime: 1692226077142,
    name: "workflow_cool",
    description:
      "Edit or extend this sample workflow. Set the workflow name to get started",
    version: 1,
    failureWorkflow: "",
    schemaVersion: 2,
    restartable: true,
    workflowStatusListenerEnabled: false,
    ownerEmail: "najeeb.thangal@orkes.io",
    timeoutSeconds: 0,
    tasks: [],
  },
  {
    updateTime: 1692226077142,
    name: "workflow_cool",
    description:
      "Edit or extend this sample workflow. Set the workflow name to get started",
    version: 2,
    failureWorkflow: "",
    schemaVersion: 2,
    restartable: true,
    workflowStatusListenerEnabled: false,
    ownerEmail: "najeeb.thangal@orkes.io",
    timeoutSeconds: 0,
    tasks: [],
  },
  {
    updateTime: 1692226077142,
    name: "workflow_cool",
    description:
      "Edit or extend this sample workflow. Set the workflow name to get started",
    version: 3,
    failureWorkflow: "",
    schemaVersion: 2,
    restartable: true,
    workflowStatusListenerEnabled: false,
    ownerEmail: "najeeb.thangal@orkes.io",
    timeoutSeconds: 0,
    tasks: [],
  },
  {
    updateTime: 1692226077142,
    name: "new workflow",
    description:
      "Edit or extend this sample workflow. Set the workflow name to get started",
    version: 1,
    failureWorkflow: "",
    schemaVersion: 2,
    restartable: true,
    workflowStatusListenerEnabled: false,
    ownerEmail: "najeeb.thangal@orkes.io",
    timeoutSeconds: 0,
    tasks: [],
  },
];
const scheduler = ["sheducle", "new", "raju_schedule"];

describe("Check SearchResultExtractor function", () => {
  it("Should return the expected result for core OSS categories", () => {
    const searchTerm = "test";
    const expectedResult = [
      {
        title: "Task Definitions",
        route: "/taskDef",
        sub: [
          {
            route: "/taskDef",
            title: "View all task definitions",
          },
          {
            route: "/taskDef/najeeb_test",
            title: "najeeb_test",
          },
        ],
      },
      {
        title: "Workflows",
        route: "/workflowDef",
        sub: [
          {
            route: "/workflowDef",
            title: "View all workflow definitions",
          },
        ],
      },
      {
        title: "Schedules",
        route: "/scheduleDef",
        sub: [
          {
            route: "/scheduleDef",
            title: "View all schedulers",
          },
        ],
      },
      {
        title: "Events",
        route: "/eventHandlerDef",
        sub: [
          {
            route: "/eventHandlerDef",
            title: "View all events",
          },
        ],
      },
    ];

    const validation = searchResultExtractor({
      taskDefinitions,
      searchTerm,
    });
    expect(expectedResult).toEqual(validation);
  });

  it("Should return the results with workflow_cool", () => {
    const searchTerm = "workflow_cool";
    const expected = [
      {
        title: "Workflows",
        route: "/workflowDef",
        sub: [
          {
            route: "/workflowDef",
            title: "View all workflow definitions",
          },
          {
            route: "/workflowDef/workflow_cool",
            title: "workflow_cool",
          },
        ],
      },
      {
        title: "Task Definitions",
        route: "/taskDef",
        sub: [
          {
            route: "/taskDef",
            title: "View all task definitions",
          },
        ],
      },
      {
        title: "Schedules",
        route: "/scheduleDef",
        sub: [
          {
            route: "/scheduleDef",
            title: "View all schedulers",
          },
        ],
      },
      {
        title: "Events",
        route: "/eventHandlerDef",
        sub: [
          {
            route: "/eventHandlerDef",
            title: "View all events",
          },
        ],
      },
    ];

    const validation = searchResultExtractor({
      taskDefinitions,
      workflowDefinitions,
      scheduler,
      searchTerm,
    });

    expect(expected).toEqual(validation);
  });

  it("Should return empty array when no matches", () => {
    const searchTerm = "hduauduhaehfahhaehfaehihiufhaihahfhaehfahehaiu";
    const validation = searchResultExtractor({
      taskDefinitions,
      searchTerm,
    });

    const viewAllAsResults = [
      {
        title: "Workflows",
        route: "/workflowDef",
        sub: [
          {
            route: "/workflowDef",
            title: "View all workflow definitions",
          },
        ],
      },
      {
        title: "Task Definitions",
        route: "/taskDef",
        sub: [
          {
            route: "/taskDef",
            title: "View all task definitions",
          },
        ],
      },
      {
        title: "Schedules",
        route: "/scheduleDef",
        sub: [
          {
            route: "/scheduleDef",
            title: "View all schedulers",
          },
        ],
      },
      {
        title: "Events",
        route: "/eventHandlerDef",
        sub: [
          {
            route: "/eventHandlerDef",
            title: "View all events",
          },
        ],
      },
    ];

    expect(viewAllAsResults).toEqual(validation);
  });

  it("Should return null", () => {
    const searchTerm = "";
    const validation = searchResultExtractor({
      taskDefinitions,
      searchTerm,
    });

    expect(null).toEqual(validation);
  });
});

const menuCases: {
  description: string;
  menuItems: MenuItemType[];
  expected: { route: string; title: string }[];
}[] = [
  {
    description: "Menu doesn't have nested & hidden menu items",
    menuItems: [
      {
        id: "menuA",
        title: "Test menu A",
        linkTo: "/test-menu-a",
        shortcuts: [],
        icon: "",
        hidden: false,
      },
      {
        id: "menuB",
        title: "Test menu B",
        linkTo: "/test-menu-b",
        shortcuts: [],
        icon: "",
        hidden: false,
      },
    ],
    expected: [
      {
        title: "Test menu A",
        route: "/test-menu-a",
      },
      {
        title: "Test menu B",
        route: "/test-menu-b",
      },
    ],
  },
  {
    description: "Menu without nested menu items, has hidden items",
    menuItems: [
      {
        id: "menuA",
        title: "Test menu A",
        linkTo: "/test-menu-a",
        shortcuts: [],
        icon: "",
        hidden: false,
      },
      {
        id: "menuB",
        title: "Test menu B",
        linkTo: "/test-menu-b",
        shortcuts: [],
        icon: "",
        hidden: true,
      },
      {
        id: "menuC",
        title: "Test menu C",
        linkTo: "/test-menu-c",
        shortcuts: [],
        icon: "",
        hidden: true,
      },
    ],
    expected: [
      {
        title: "Test menu A",
        route: "/test-menu-a",
      },
    ],
  },
  {
    description: "Menu has nested and hidden items",
    menuItems: [
      {
        id: "menuA",
        title: "Test menu A",
        linkTo: "/test-menu-a",
        shortcuts: [],
        icon: "",
        hidden: false,
        items: [
          {
            id: "menuA1",
            title: "Test menu A1",
            linkTo: "/test-menu-a1",
            shortcuts: [],
            icon: "",
            hidden: false,
          },
          {
            id: "menuA2",
            title: "Test menu A2",
            linkTo: "/test-menu-a2",
            shortcuts: [],
            icon: "",
            hidden: true,
          },
          {
            id: "menuA3",
            title: "Test menu A3",
            linkTo: "/test-menu-a3",
            shortcuts: [],
            icon: "",
            hidden: false,
          },
        ],
      },
      {
        id: "menuB",
        title: "Test menu B",
        linkTo: "/test-menu-b",
        shortcuts: [],
        icon: "",
        hidden: false,
      },
    ],
    expected: [
      {
        title: "Test menu A - Test menu A1",
        route: "/test-menu-a1",
      },
      {
        title: "Test menu A - Test menu A3",
        route: "/test-menu-a3",
      },
      {
        title: "Test menu B",
        route: "/test-menu-b",
      },
    ],
  },
];

describe("Check flattenMenu function", () => {
  test.each(menuCases)(
    "Testing case: $description",
    ({ menuItems, expected }) => {
      const result = flattenMenu(menuItems);

      expect(result).toMatchObject(expected);
    },
  );
});
