import { CreateAndDisplayApplicationMachineContext } from "./types";
import { User } from "types/User";
export declare const createApplication: (context: CreateAndDisplayApplicationMachineContext) => Promise<any>;
export declare const fetchForAppDetails: (context: CreateAndDisplayApplicationMachineContext) => Promise<User>;
export declare const checkIfAppExistsAndCompatible: (context: CreateAndDisplayApplicationMachineContext) => Promise<{
    id: any;
}>;
export declare const createApplicationWithRoles: (context: CreateAndDisplayApplicationMachineContext) => Promise<any>;
export declare const generateKeys: (context: CreateAndDisplayApplicationMachineContext) => Promise<any>;
