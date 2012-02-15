//  Copyright 2012 Jean-Francois Poilpret
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package json;

import java.util.List;
import java.util.Map;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import yeti.lang.FailureException;
import yeti.lang.AList;
import yeti.lang.Fun;
import yeti.lang.GenericStruct;
import yeti.lang.MList;
import yeti.lang.Num;
import yeti.lang.Struct;
import yeti.lang.Tag;

//TODO support conversion of JSONObject into Hash (not just Struct)
public final class YetiJson
{
	static final private Tag NONE = new Tag(null, "None");
	static final private String SOME_TAG = "Some";
	
	static public Object parse(Object template, String input) throws ParseException
	{
		return convertValue(template, _parser.get().parse(input));
	}

	@SuppressWarnings("rawtypes") 
	static public Object parse(List templates, String input) throws ParseException
	{
		if (templates.isEmpty())
		{
			failWith("templates must not be the empty list []");
		}
		// Check that templates are variants
		for (Object t: templates)
		{
			if (t instanceof Tag)
			{
				Tag tag = (Tag) t;
				try
				{
					return new Tag(parse(tag.value, input), tag.name);
				}
				catch (Exception e)
				{
					// Continue check of next template
				}
			}
			else
			{
				failWith("templates must only contain variant items");
			}
		}
		failWith("input doesn't match any template");
		return null;
	}

	@SuppressWarnings("rawtypes") 
	static private AList convertList(AList expected, List value)
	{
		if (value.isEmpty())
		{
			return null;
		}
		
		MList result = new MList();
		result.reserve(value.size());
		for (Object item: value)
		{
			result.add(convertValue(expected.first(), item));
		}
		return result;
	}
	
	@SuppressWarnings("rawtypes") 
	static private AList convertList(List value)
	{
		if (value.isEmpty())
		{
			return null;
		}
		
		MList result = new MList();
		result.reserve(value.size());
		for (Object item: value)
		{
			result.add(convertValue(item));
		}
		return result;
	}
	
	@SuppressWarnings("rawtypes") 
	static private Struct convertStruct(Struct expected, Map value)
	{
		String[] fields = new String[expected.count()];
		for (int i = 0; i < fields.length; i++)
		{
			fields[i] = expected.name(i);
		}
		Struct result = new GenericStruct(fields, null);
		for (int i = 0; i < fields.length; i++)
		{
			convertField(expected, result, value, fields[i]);
		}
		
		return result;
	}

	@SuppressWarnings("rawtypes") 
	static private void convertField(Struct expected, Struct result, Map values, String name)
	{
		if (values.containsKey(name))
		{
			result.set(name, convertValue(expected.get(name), values.get(name)));
		}
		else
		{
			failWith(String.format("No entry with name `%s`", name));
		}
	}

	//TODO Make this method public
	//TODO Try to add special conversion functions if needed
	@SuppressWarnings("rawtypes") 
	static private Object convertValue(Object expected, Object value)
	{
		if (expected instanceof Struct && value instanceof Map)
		{
			return convertStruct((Struct) expected, (Map) value);
		}
		else if (expected == null && value instanceof List)
		{
			return convertList((List) value);
		}
		else if (expected instanceof AList && value instanceof List)
		{
			return convertList((AList) expected, (List) value);
		}
		else if (expected instanceof Num && value instanceof Number)
		{
			return Num.parseNum(value.toString());
		}
		else if (expected instanceof String && value instanceof String)
		{
			return value;
		}
		else if (expected instanceof Boolean && value instanceof Boolean)
		{
			return value;
		}
		else if (isSome(expected))
		{
			//TODO
			if (value == null)
			{
				return _none;
			}
			else
			{
				return new SomeFun(convertValue(expectedFromSome(expected), value));
			}
		}
//		else if (expected instanceof Tag && SOME_TAG.equals(((Tag) expected).name))
//		{
//			if (value == null)
//			{
//				return NONE;
//			}
//			else
//			{
//				Tag tag = (Tag) expected;
//				return new Tag(convertValue(tag.value, value), SOME_TAG);
//			}
//		}
		else if (value == null && (expected == null || expected instanceof AList))
		{
			return null;
		}
		else
		{
			failWith("Actual value doesn't match expected type");
			return null;
		}
	}

	@SuppressWarnings("rawtypes") 
	static private Object convertValue(Object value)
	{
		if (value instanceof Map)
		{
			//TODO how to convert a JSON object into any expected type?
			return null;
		}
		else if (value instanceof List)
		{
			return convertList((List) value);
		}
		else if (value instanceof Number)
		{
			return Num.parseNum(value.toString());
		}
		else if (value instanceof String)
		{
			return value;
		}
		else if (value instanceof Boolean)
		{
			return value;
		}
		else
		{
			failWith("Actual value cannot be converted");
			return null;
		}
	}
	
	static private boolean isSome(Object expected)
	{
		if (expected instanceof Fun)
		{
			try
			{
				Object result = ((Fun) expected).apply(null);
				return		result instanceof Tag
						&&	SOME_TAG.equals(((Tag) result).name);
			}
			catch (Exception e)
			{
				// "some" function should never throw exception, thus this is not it
			}
		}
		return false;
	}

	static private Object expectedFromSome(Object expected)
	{
		Tag result = (Tag) ((Fun) expected).apply(null);
		return result.value;
	}

	static private void failWith(String message)
	{
		throw new FailureException(message);
	}

	static final private class SomeFun extends Fun
	{
		public SomeFun(Object value)
		{
			_value = new Tag(value, SOME_TAG);
		}
		
		@Override public Object apply(Object arg)
		{
			return _value;
		}
		
		final private Tag _value;
	}
	
	static final private Fun _none = new Fun()
	{
		@Override public Object apply(Object arg)
		{
			return NONE;
		}
	};
	
	static final private ThreadLocal<JSONParser> _parser = new ThreadLocal<JSONParser>()
	{
		@Override protected JSONParser initialValue()
		{
			return new JSONParser();
		}
	};
}
