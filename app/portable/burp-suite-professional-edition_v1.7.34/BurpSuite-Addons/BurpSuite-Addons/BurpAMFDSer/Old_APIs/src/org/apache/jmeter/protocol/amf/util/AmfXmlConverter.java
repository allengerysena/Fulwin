/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.protocol.amf.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.mapper.Mapper;

import flex.messaging.io.ClassAliasRegistry;
import flex.messaging.io.MessageDeserializer;
import flex.messaging.io.SerializationContext;
import flex.messaging.io.amf.ASObject;
import flex.messaging.io.amf.ActionContext;
import flex.messaging.io.amf.ActionMessage;
import flex.messaging.io.amf.Amf3Input;
import flex.messaging.io.amf.Amf3Output;
import flex.messaging.io.amf.AmfMessageDeserializer;
import flex.messaging.io.amf.AmfMessageSerializer;
import flex.messaging.io.amf.MessageBody;
import flex.messaging.io.amf.MessageHeader;
import flex.messaging.messages.AcknowledgeMessage;
import flex.messaging.messages.AcknowledgeMessageExt;
import flex.messaging.messages.AsyncMessage;
import flex.messaging.messages.CommandMessage;
import flex.messaging.messages.CommandMessageExt;
import flex.messaging.messages.ErrorMessage;
import flex.messaging.messages.Message;
import flex.messaging.messages.RemotingMessage;

public class AmfXmlConverter {

	private static XStream xstream;

	/**
	 * Converts XML to an object then serializes it
	 */
	public static byte[] convertXmlToAmf(String xml) {
		XStream xs = getXStream();
		Amf3Output amf3out = new Amf3Output(SerializationContext.getSerializationContext());

		try {
			Object msg = xs.fromXML(xml);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			amf3out.setOutputStream(baos);
			amf3out.writeObject(msg);

			return baos.toByteArray();
		} catch (Exception ex) {
		}

		return new byte[0];
	}

	/**
	 * Converts AMF encoded object to XML String
	 */
	public static String convertAmfToXml(byte[] amf) {
		XStream xs = getXStream();
		SerializationContext serializationContext = new SerializationContext();
		serializationContext.createASObjectForMissingType = true;
		serializationContext.instantiateTypes = true;
		Amf3Input amf3in = new Amf3Input(serializationContext);
		ByteArrayInputStream bais = new ByteArrayInputStream(amf);
		amf3in.setInputStream(bais);
		try {
			Object obj = amf3in.readObject();
			String xml = xs.toXML(obj);
			return xml;
		} catch (Exception ex) {
		} finally {
			try {
				amf3in.close();
			} catch (IOException e) {

			}
		}

		return "";
	}

	/**
	 * Converts XML to a complete AMF message
	 */
	public static byte[] convertXmlToAmfMessage(String xml) {

		XStream xs = getXStream();
		ActionMessage message = (ActionMessage) xs.fromXML(xml);
		// if (checkAckMessage(message))
		// return null;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		ActionContext actionContext = new ActionContext();
		actionContext.setRequestMessage(message);

		AmfMessageSerializer amfMessageSerializer = new AmfMessageSerializer();
		SerializationContext serializationContext = SerializationContext.getSerializationContext();
		amfMessageSerializer.initialize(serializationContext, baos, null);

		try {
			amfMessageSerializer.writeMessage(message);
			return baos.toByteArray();
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		} finally {
			baos = null;
		}
	}

	/**
	 * Converts complete AMF message to XML representation
	 */
	public static String convertAmfMessageToXml(byte[] amf) {
		return AmfXmlConverter.convertAmfMessageToXml(amf, false);
	}

	public static String convertAmfMessageToXml(byte[] amf, boolean useAliasRegistry) {
		XStream xs = getXStream();
		ActionContext actionContext = new ActionContext();
		SerializationContext serializationContext = new SerializationContext();

		// Class aliases for deserialization, mimics registerClassAlias in Flex
		// Generally only used in rendering as it can cause serious problems for
		// proxy sampling
		if (useAliasRegistry) {
			ClassAliasRegistry aliases = ClassAliasRegistry.getRegistry();
			aliases.registerAlias("DSC", "flex.messaging.messages.CommandMessageExt"); // This
			aliases.registerAlias("DSK", "flex.messaging.messages.AcknowledgeMessageExt");
		}
		serializationContext.createASObjectForMissingType = true;
		serializationContext.enableSmallMessages = false;

		ByteArrayInputStream bin = new ByteArrayInputStream(amf);
		ActionMessage message = new ActionMessage();

		MessageDeserializer deserializer = new AmfMessageDeserializer();
		deserializer.initialize(serializationContext, bin, null);

		try {
			deserializer.readMessage(message, actionContext);
			if (checkAckMessage(message))
				return null;
			String xml = xs.toXML(message);
			return xml;

		} catch (Exception ex) {
//			ex.printStackTrace();
			return null;
		}
	}

	private static boolean checkAckMessage(ActionMessage message) {
		Object data = message.getBody(0).getData();
		Object cloneData = data;
		int bodyCount = message.getBodyCount();
		// System.out.println("*********** OBJDUMP: " + objDump(message));
		if (bodyCount == 0)
			return true;
		else if (bodyCount == 1) {
			if (data instanceof List)
				cloneData = ((List) data).get(0);
			else if (data.getClass().isArray())
				cloneData = Array.get(data, 0);
			if (cloneData instanceof ASObject) {
				String type = ((ASObject) cloneData).getType();
				if (type.matches("DSC|DSK"))
					return true;
			}
		}
		return false;
	}

	public static XStream getXStream() {
		if (xstream == null) {
			xstream = new XStream(new DomDriver());

			xstream.alias("ActionMessage", ActionMessage.class);
			xstream.alias("MessageHeader", MessageHeader.class);
			xstream.alias("MessageBody", MessageBody.class);
			xstream.alias("RemotingMessage", RemotingMessage.class);
			xstream.alias("CommandMessage", CommandMessage.class);
			xstream.alias("AcknowledgeMessage", AcknowledgeMessage.class);
			xstream.alias("ErrorMessage", ErrorMessage.class);
			xstream.alias("ASObject", ASObject.class);
			xstream.alias("AsyncMessage", AsyncMessage.class);
			xstream.alias("DSC", CommandMessageExt.class);
			xstream.alias("DSK", AcknowledgeMessageExt.class);

			// Better ASObject Converter
			Mapper mapper = xstream.getMapper();
			xstream.registerConverter(new ASObjectConverter(mapper));
		}

		return xstream;
	}

	public static String objDump(Object o) {
		return xstream.toXML(o);
	}

}
