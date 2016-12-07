/**
 * 
 */
package com.netflix.conductor.demo;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.servlet.GuiceFilter;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.tests.utils.JerseyModule;
import com.netflix.conductor.tests.utils.TestModule;

/**
 * @author Viren
 * Main class to launch the conductor server with in-memory persistence.
 */
public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
		EmbeddedElasticSearch.start();
		Guice.createInjector(new TestModule(), new JerseyModule());
		
		ClassLoader loader = Main.class.getClassLoader();
		File indexLoc = new File(loader.getResource("webapp/swagger-ui/index.html").getFile());
		String htmlLoc = indexLoc.getParentFile().getParentFile().getAbsolutePath();
		
		Server server = new Server(8080);

		ServletContextHandler context = new ServletContextHandler();
		context.addFilter(GuiceFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
		context.setResourceBase(htmlLoc);
		context.setWelcomeFiles(new String[] { "index.html" });
		server.setHandler(context);


		DefaultServlet staticServlet = new DefaultServlet();
		context.addServlet(new ServletHolder(staticServlet), "/*");
		
		server.start();
		System.out.println("Started server on http://localhost:8080/");
		
		createKitchenSink();
		
		server.join();
	}
	
	private static void createKitchenSink() throws Exception {
		TaskClient tc = new TaskClient();
		tc.setRootURI("http://localhost:8080/api/");
		
		WorkflowClient wc = new WorkflowClient();
		wc.setRootURI("http://localhost:8080/api/");
		
		List<TaskDef> taskDefs = new LinkedList<>();
		for(int i = 0; i < 40; i++) {
			taskDefs.add(new TaskDef("task_" + i, "task_" + i, 1, 0));
		}
		tc.registerTaskDefs(taskDefs);
		
		
		URL template = Main.class.getClassLoader().getResource("wf1.json");
		byte[] source = Files.readAllBytes(Paths.get(template.getFile()));
		WorkflowDef def = new ObjectMapper().readValue(source, WorkflowDef.class);
		wc.registerWorkflow(def);
		
		template = Main.class.getClassLoader().getResource("wf2.json");
		source = Files.readAllBytes(Paths.get(template.getFile()));
		def = new ObjectMapper().readValue(source, WorkflowDef.class);
		wc.registerWorkflow(def);
		
		System.out.println("Kitchen sink workflows are created!");
	}
}
