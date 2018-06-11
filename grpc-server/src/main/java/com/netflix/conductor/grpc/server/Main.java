package com.netflix.conductor.grpc.server;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Main {

    public static void main(String args[]) throws Exception {

        //FIXME This was copy pasted and seems like a bad way to load a config, given that is has side affects.
        loadConfigFile(args.length > 0 ? args[0] : System.getenv("CONDUCTOR_CONFIG_FILE"));

        if (args.length == 2) {
            System.out.println("Using log4j config " + args[1]);
            PropertyConfigurator.configure(new FileInputStream(new File(args[1])));
        }

        Injector injector = Guice.createInjector(new GRPCModule());
        GRPCServer server = injector.getInstance(GRPCServer.class);

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                server.stop();
            }
        }));

        System.out.println("\n\n\n");
        System.out.println("                     _            _             ");
        System.out.println("  ___ ___  _ __   __| |_   _  ___| |_ ___  _ __ ");
        System.out.println(" / __/ _ \\| '_ \\ / _` | | | |/ __| __/ _ \\| '__|");
        System.out.println("| (_| (_) | | | | (_| | |_| | (__| || (_) | |   ");
        System.out.println(" \\___\\___/|_| |_|\\__,_|\\__,_|\\___|\\__\\___/|_|   ");
        System.out.println("\n\n\n");

        server.start();
    }

    private static void loadConfigFile(String propertyFile) throws IOException {
        if (propertyFile == null) return;
        System.out.println("Using config file" + propertyFile);
        Properties props = new Properties(System.getProperties());
        props.load(new FileInputStream(propertyFile));
        System.setProperties(props);
    }
}
