/**
 * 
 */
package com.netflix.conductor.core.events;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * @author Viren
 *
 */
public class ScriptEvaluator {

	private static ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
	
	private ScriptEvaluator(){
		
	}
	
	public static Boolean evalBool(String script, Object input) throws ScriptException {
		Object ret = eval(script, input);
		
		if(ret instanceof Boolean) {
			return ((Boolean)ret);
		}else if(ret instanceof Number) {
			return ((Number)ret).doubleValue() > 0;
		}
		return false;
	}
	
	public static Object eval(String script, Object input) throws ScriptException {
		Bindings bindings = engine.createBindings();
		bindings.put("$", input);
		return engine.eval(script, bindings);
		
	}
}
