package edu.umass.cs.contextservice.messages;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.contextservice.logging.ContextServiceLogger;
import edu.umass.cs.gns.nio.IntegerPacketType;
import edu.umass.cs.gns.nio.JSONPacket;
import edu.umass.cs.gns.protocoltask.ProtocolEvent;
import edu.umass.cs.gns.protocoltask.ProtocolTask;
import edu.umass.cs.gns.protocoltask.json.ProtocolPacket;
import edu.umass.cs.gns.util.IntegerPacketTypeMap;

/**
 * 
 * @author adipc
 * 
 * @param <NodeIDType>
 */
public abstract class ContextServicePacket<NodeIDType> extends ProtocolPacket<NodeIDType, ContextServicePacket.PacketType> { 

	public static final String PACKET_TYPE = JSONPacket.PACKET_TYPE;
	public static final String HANDLER_METHOD_PREFIX = "handle";

	/********************************* End of ContextServicePacket ***********************/
	public enum PacketType implements IntegerPacketType
	{
		// metadata mesg
		METADATA_MSG_TO_VALUENODE(1),
		// query mesg
		QUERY_MSG_FROM_USER(2),
		// predicate mesg
		QUERY_MSG_TO_METADATANODE(3),
		// valuenode mesg
		QUERY_MSG_TO_VALUENODE(4), // on an add request replica controller sends to active replica
		// valuenode reply mesg
		QUERY_MSG_TO_VALUENODE_REPLY(5), // after adding name, active replica confirms to replica controller
		VALUE_UPDATE_MSG_TO_METADATANODE(6), // value update mesg to meta data node
		VALUE_UPDATE_MSG_TO_VALUENODE(7), // value update mesg to value node.
		VALUE_UPDATE_MSG_FROM_GNS(8),     // value update trigger from GNS
		QUERY_MSG_FROM_USER_REPLY(9),     // reply to the query mesg from user, reply goes back to the original querier
		VALUE_UPDATE_MSG_FROM_GNS_REPLY(10),  // reply that goes back to GNS or whoever issues this message.
		VALUE_UPDATE_MSG_TO_VALUENODE_REPLY(11),  // valuenode reply to ?
		REFRESH_TRIGGER(12);   // trigger sent to the querier to refresh
		
		private final int number;

		PacketType(int t) {this.number = t;}
		public int getInt() {return number;}

		public static final IntegerPacketTypeMap<PacketType> intToType = 
				new IntegerPacketTypeMap<PacketType>(PacketType.values());
		
		//public static ContextServicePacket.PacketType[] getPacketTypes()
		public static List<ContextServicePacket.PacketType> getPacketTypes()
		{
			IntegerPacketTypeMap<PacketType> packetTypeMap = 
					ContextServicePacket.PacketType.intToType;
			LinkedList<ContextServicePacket.PacketType> packetTypeList = 
					new LinkedList<ContextServicePacket.PacketType>();
			
			packetTypeList.addAll(packetTypeMap.values());
			
			ContextServiceLogger.getLogger().info("size "+packetTypeList.size() + 
					" packetTypeList "+packetTypeList);
			
			//return (PacketType[]) packetTypeList.toArray();
			return packetTypeList;
		}
	}
	
	/********************************* End of ContextServicePacketType ***********************/

	/**************************** Start of ContextServicePacketType class map **************/
	private static final HashMap<ContextServicePacket.PacketType, Class<?>> typeMap = 
			new HashMap<ContextServicePacket.PacketType, Class<?>>();
	static 
	{
		/* This map prevents the need for laborious switch/case sequences as it automatically
		 * handles both json-to-ContextServicePacket conversion and invocation of the 
		 * corresponding handler method. We have to rely on reflection for both and the 
		 * cost of the former seems to be the bottleneck as it adds ~25us per conversion,
		 * but it seems not problematic for now. 
		 */
		typeMap.put(ContextServicePacket.PacketType.METADATA_MSG_TO_VALUENODE, MetadataMsgToValuenode.class);
		typeMap.put(ContextServicePacket.PacketType.QUERY_MSG_FROM_USER, QueryMsgFromUser.class);
		typeMap.put(ContextServicePacket.PacketType.QUERY_MSG_TO_METADATANODE, QueryMsgToMetadataNode.class);
		typeMap.put(ContextServicePacket.PacketType.QUERY_MSG_TO_VALUENODE, QueryMsgToValuenode.class);
		typeMap.put(ContextServicePacket.PacketType.QUERY_MSG_TO_VALUENODE_REPLY, QueryMsgToValuenodeReply.class);
		typeMap.put(ContextServicePacket.PacketType.VALUE_UPDATE_MSG_TO_METADATANODE, ValueUpdateMsgToMetadataNode.class);
		typeMap.put(ContextServicePacket.PacketType.VALUE_UPDATE_MSG_TO_VALUENODE, ValueUpdateMsgToValuenode.class);
		typeMap.put(ContextServicePacket.PacketType.VALUE_UPDATE_MSG_FROM_GNS, ValueUpdateFromGNS.class);
		typeMap.put(ContextServicePacket.PacketType.QUERY_MSG_FROM_USER_REPLY, QueryMsgFromUserReply.class);
		typeMap.put(ContextServicePacket.PacketType.VALUE_UPDATE_MSG_FROM_GNS_REPLY, ValueUpdateFromGNSReply.class);
		typeMap.put(ContextServicePacket.PacketType.VALUE_UPDATE_MSG_TO_VALUENODE_REPLY, ValueUpdateMsgToValuenodeReply.class);
		typeMap.put(ContextServicePacket.PacketType.REFRESH_TRIGGER, ValueUpdateMsgToValuenodeReply.class);
		
		
		for(ContextServicePacket.PacketType type : ContextServicePacket.PacketType.intToType.values())
		{
			assert(getPacketTypeClassName(type)!=null) : type;
		}
	}
	/**************************** End of ReconfigurationpacketType class map **************/

	// FIXME: probably should be removed
	protected ContextServicePacket(NodeIDType initiator)
	{
		super(initiator);
		//this.setType(t);
	}

	public ContextServicePacket(JSONObject json) throws JSONException
	{
		super(json);
		this.setType(getPacketType(json));
	}

	@Override
	public JSONObject toJSONObjectImpl() throws JSONException 
	{
		JSONObject json = new JSONObject();
		return json;
	}

	@Override
	public Object getMessage() 
	{
		return this;
	}

	@Override
	public PacketType getPacketType(JSONObject json)
			throws JSONException 
	{
		return getContextServicePacketType(json);
	}

	@Override
	public void putPacketType(JSONObject json, PacketType type)
			throws JSONException 
	{
		json.put(PACKET_TYPE, type.getInt());
	}
	
	public String toString() 
	{
		try {
			return this.toJSONObject().toString();
		} catch(JSONException je) {
			je.printStackTrace();
		}
		return null;
	}
	
	public static final ContextServicePacket.PacketType getContextServicePacketType(JSONObject json) throws JSONException
	{
		if(json.has(ContextServicePacket.PACKET_TYPE))
			return ContextServicePacket.PacketType.intToType.get(json.getInt(PACKET_TYPE));
		else return null;
	}
	
	public static final String getPacketTypeClassName(ContextServicePacket.PacketType type) 
	{
		return typeMap.get(type)!=null ? typeMap.get(type).getSimpleName() : null;
	}

	public static BasicContextServicePacket<?> getContextServicePacket(JSONObject json, 
		Map<ContextServicePacket.PacketType,Class<?>> typeMap) throws JSONException 
	{
		BasicContextServicePacket<?> csPacket = null;
		try 
		{
			
			ContextServicePacket.PacketType csType = 
					ContextServicePacket.PacketType.intToType.get(JSONPacket.getPacketType(json)); 
			//System.out.println("\n\n\n packet type recvd "+csType+" json "+json);
			if(csType!=null && getPacketTypeClassName(csType)!=null) 
			{
				csPacket = (BasicContextServicePacket<?>)(Class.forName(
						"edu.umass.cs.contextservice.messages." + 
				getPacketTypeClassName(csType)).getConstructor(JSONObject.class).newInstance(json));
			}
		}
		catch(NoSuchMethodException nsme) {nsme.printStackTrace();} 
		catch(InvocationTargetException ite) {ite.printStackTrace();} 
		catch(IllegalAccessException iae) {iae.printStackTrace();} 
		catch(ClassNotFoundException cnfe) {cnfe.printStackTrace();}
		catch(InstantiationException ie) {ie.printStackTrace();}
		
		return csPacket;
	}
	
	public static BasicContextServicePacket<?> getContextServicePacket(JSONObject json) throws JSONException
	{
		return getContextServicePacket(json, typeMap);
	}
	
	public PacketType getAllPacketTypes(JSONObject json)
			throws JSONException 
	{
		return getContextServicePacketType(json);
	}
	
	/************************* Start of assertion methods **************************************************/ 
	/* The assertion methods below are just convenience methods to let protocoltasks 
	 * assert that they have set up handlers for all packet types for which they
	 * are responsible.
	 */
	public static void assertPacketTypeChecks(ContextServicePacket.PacketType[] types, Class<?> target, String handlerMethodPrefix) 
	{
		for(ContextServicePacket.PacketType type : types) 
		{
			assertPacketTypeChecks(type, getPacketTypeClassName(type), target, handlerMethodPrefix);			
		}
	}
	
	public static void assertPacketTypeChecks(Map<ContextServicePacket.PacketType,Class<?>> typeMap, Class<?> target, 
			String handlerMethodPrefix) 
	{
		// Assertions ensure that method name changes do not break code.
		for(ContextServicePacket.PacketType type : typeMap.keySet()) {
			assertPacketTypeChecks(type, getPacketTypeClassName(type), target, handlerMethodPrefix);
		}
	}
	
	public static void assertPacketTypeChecks(ContextServicePacket.PacketType type, String packetName, 
			Class<?> target, String handlerMethodPrefix) 
	{
		String errMsg = "Method " + handlerMethodPrefix+packetName +
				" does not exist in ReconfiguratorProtocolTask";
		try {
			System.out.println(type + " : " + packetName);
			if(packetName!=null)
				assert(target.getMethod(handlerMethodPrefix+packetName, 
					ProtocolEvent.class, ProtocolTask[].class)!=null) : 
						errMsg;
		} catch(NoSuchMethodException nsme) {
			System.err.println(errMsg);
			nsme.printStackTrace();
		}
	}
	
	public static void assertPacketTypeChecks(Map<ContextServicePacket.PacketType,Class<?>> typeMap, Class<?> target) 
	{
		assertPacketTypeChecks(typeMap, target, HANDLER_METHOD_PREFIX);
	}
	
	public static ContextServicePacket.PacketType[] concatenate(ContextServicePacket.PacketType[]... types) 
	{
		int size=0;
		for(ContextServicePacket.PacketType[] tarray : types) size += tarray.length;
		ContextServicePacket.PacketType[] allTypes = new ContextServicePacket.PacketType[size];
		int i=0;
		for(ContextServicePacket.PacketType[] tarray : types) {
			for(ContextServicePacket.PacketType type : tarray) {
				allTypes[i++] = type;
			}
		}
		return allTypes;
	}
	/************************* End of assertion methods **************************************************/ 

	public static void main(String[] args) 
	{
		System.out.println(ContextServicePacket.PacketType.intToType.get(225));
	}
}