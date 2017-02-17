/**
 * 
 */
package com.netflix.conductor.core.events;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * @author Viren
 *
 */
public class TestScriptEval {

	@Test
	public void testScript() throws Exception {
		Map<String, Object> payload = new HashMap<>();
		Map<String, Object> app = new HashMap<>();
		app.put("name", "conductor");
		app.put("version", 2.0);
		app.put("license", "Apace 2.0");
		
		payload.put("app", app);
		payload.put("author", "Netflix");
		payload.put("oss", true);
		
		String script1 = "$.app.name == 'conductor'";		//true
		String script2 = "$.version > 3";					//false
		String script3 = "$.oss";							//true
		String script4 = "$.author == 'me'";				//false
		
		assertTrue(ScriptEvaluator.evalBool(script1, payload));
		assertFalse(ScriptEvaluator.evalBool(script2, payload));
		assertTrue(ScriptEvaluator.evalBool(script3, payload));
		assertFalse(ScriptEvaluator.evalBool(script4, payload));
		
	}
}
