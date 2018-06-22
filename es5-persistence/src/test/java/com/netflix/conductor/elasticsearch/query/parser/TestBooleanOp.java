/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.netflix.conductor.elasticsearch.query.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Viren
 *
 */
public class TestBooleanOp extends AbstractParserTest {

	@Test
	public void test() throws Exception {
		String[] tests = new String[]{"AND", "OR"};
		for(String test : tests){
			BooleanOp name = new BooleanOp(getInputStream(test));
			String nameVal = name.getOperator();
			assertNotNull(nameVal);
			assertEquals(test, nameVal);
		}
	}
	
	@Test(expected=ParserException.class)
	public void testInvalid() throws Exception {
		String test = "<";
		BooleanOp name = new BooleanOp(getInputStream(test));
		String nameVal = name.getOperator();
		assertNotNull(nameVal);
		assertEquals(test, nameVal);
	
	}
}
