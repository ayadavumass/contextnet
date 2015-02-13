package edu.umass.cs.contextservice.examples.basic;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import org.json.JSONArray;
import org.json.JSONException;

import edu.umass.cs.contextservice.CSNodeConfig;
import edu.umass.cs.contextservice.ContextServiceNode;
import edu.umass.cs.contextservice.config.ContextServiceConfig;
import edu.umass.cs.contextservice.gns.GNSCalls;
import edu.umass.cs.contextservice.messages.QueryMsgFromUser;
import edu.umass.cs.contextservice.messages.ValueUpdateMsgToMetadataNode;
import edu.umass.cs.contextservice.utils.Utils;
import edu.umass.cs.gns.nio.GenericMessagingTask;
import edu.umass.cs.gns.nio.InterfaceNodeConfig;

/**
 * 
 * Simple context service example with 3 nodes, with simple
 * conjunction query.
 * @author adipc
 *
 */
public class BasicContextServiceExample extends ContextServiceNode<Integer>
{
	private static CSNodeConfig<Integer> csNodeConfig					= null;
	
	private static DatagramSocket server_socket;
	
	private static BasicContextServiceExample[] nodes					= null;
	
	public BasicContextServiceExample(Integer id, InterfaceNodeConfig<Integer> nc)
			throws IOException
	{
		super(id, nc);
	}
	
	public static void main(String[] args) throws NumberFormatException, UnknownHostException, IOException
	{	
		//server_socket = new DatagramSocket(12345, InetAddress.getByName("ananas.cs.umass.edu"));
		server_socket = new DatagramSocket(12345, InetAddress.getByName("localhost"));
		/*ReconfigurableSampleNodeConfig nc = new ReconfigurableSampleNodeConfig();
		nc.localSetup(TestConfig.getNodes());*/	
		readNodeInfo();
		
		nodes = new BasicContextServiceExample[csNodeConfig.getNodes().size()];
		/*for(int i=0; i<nodes.length; i++)
		{
			nodes[i] = new BasicContextServiceExample(i+CSTestConfig.startNodeID, csNodeConfig);
		}*/
		
		for(int i=0; i<csNodeConfig.getNodes().size(); i++)
		{
			InetAddress currAddress = csNodeConfig.getNodeAddress(i+CSTestConfig.startNodeID);
			if(Utils.isMyMachineAddress(currAddress))
			{
				new Thread(new StartNode(i+CSTestConfig.startNodeID, i)).start();
				//nodes[i] = new BasicContextServiceExample(i+CSTestConfig.startNodeID, csNodeConfig);
			}
		}
		
		// print state after 10 seconds
		try
		{
			Thread.sleep(15000);
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		for(int i=0; i<nodes.length; i++)
		{
			if(nodes[i] == null)
			{
				System.out.println("\n\n NULL "+i);
			}
			else
			{
				InetAddress currAddress = csNodeConfig.getNodeAddress(i+CSTestConfig.startNodeID);
				if(Utils.isMyMachineAddress(currAddress))
				{
					System.out.println("printing state for node "+i);
					nodes[i].printNodeState();
				}
			}
		}
		
		/*try
		{
			Thread.sleep(2000);
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		// store dummy value
		nodes[0].fillDummyGUIDValues();
		try
		{
			Thread.sleep(5000);
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		nodes[0].enterQueries();*/
		/*if(nodes[0]!=null)
		{
			nodes[0].enterAndMonitorQuery();
		}*/
	}
	
	private static void readNodeInfo() throws NumberFormatException, UnknownHostException, IOException
	{
		csNodeConfig = new CSNodeConfig<Integer>();
		
		BufferedReader reader = new BufferedReader(new FileReader("nodesInfo.txt"));
		String line = null;
		while ((line = reader.readLine()) != null)
		{
			String [] parsed = line.split(" ");
			int readNodeId = Integer.parseInt(parsed[0])+CSTestConfig.startNodeID;
			InetAddress readIPAddress = InetAddress.getByName(parsed[1]);
			int readPort = Integer.parseInt(parsed[2]);
			
			csNodeConfig.add(readNodeId, new InetSocketAddress(readIPAddress, readPort));
		}
	}
	
	
//	private void fillDummyGUIDValues() throws IOException
//	{	
//		System.out.println("fillDummyGUIDValues");
//		
//		BufferedReader reader = new BufferedReader(new FileReader("dummyValues.txt"));
//		String line = null;
//		while ( (line = reader.readLine()) != null )
//		{
//			String [] parsed = line.split(" ");
//			String GUID = parsed[0];
//			for(int i=1;i<parsed.length; i++)
//			{
//				String attName = ContextServiceConfig.CONTEXT_ATTR_PREFIX+"ATT"+(i-1);
//				double attrValue = Double.parseDouble(parsed[i]);
//				ValueUpdateMsgToMetadataNode<Integer> valueUpdMsgToMetanode = 
//						new ValueUpdateMsgToMetadataNode<Integer>(this.getContextService().getMyID(), 0, GUID, attName, attrValue, attrValue, null);
//				
//				Integer respMetadataNodeId = this.getContextService().getResponsibleNodeId(attName);
//				//nioTransport.sendToID(respMetadataNodeId, valueMeta.getJSONMessage());
//				
//				GenericMessagingTask<Integer, ValueUpdateMsgToMetadataNode<Integer>> mtask = 
//						new GenericMessagingTask<Integer, ValueUpdateMsgToMetadataNode<Integer>>(respMetadataNodeId, 
//								valueUpdMsgToMetanode);
//				
//				// send the message 
//				try 
//				{
//					this.getContextService().getJSONMessenger().send(mtask);
//				} catch (JSONException e) 
//				{
//					e.printStackTrace();
//				}
//			}
//		}
//	}
	
	
	/*private void enterQueries()
	{	
		while (true)
		{
			  //  prompt the user to enter their name
		      System.out.print("\n\n\nEnter Queries here: ");
		 
		      //  open up standard input
		      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		 
		      String query = null;
		 
		      //  read the username from the command-line; need to use try/catch with the
		      //  readLine() method
		      
		      try 
		      {
				query = br.readLine();
		      } catch (IOException e1) 
		      {
				e1.printStackTrace();
		      }
		      QueryMsgFromUser<Integer> queryMsgFromUser = 
		        	new QueryMsgFromUser<Integer>(this.getContextService().getMyID(), query);
		      // nioTransport.sendToID(0, queryMesg.getJSONMessage());
		         
		      GenericMessagingTask<Integer, QueryMsgFromUser<Integer>> mtask = 
		    		  new GenericMessagingTask<Integer, QueryMsgFromUser<Integer>>
		      (this.getContextService().getMyID(), queryMsgFromUser);
					
		      // send the message 
		      try 
		      {
		    	  this.getContextService().getJSONMessenger().send(mtask);
		      } catch (JSONException e) 
		      {
		    	  e.printStackTrace();
		      } catch (IOException e) 
		      {
		    	  e.printStackTrace();
		      }
		    //System.out.println("Thanks for the name, " + userName);
	   }
	}
	
	private void enterAndMonitorQuery()
	{	
		while (true)
		{
			  //  prompt the user to enter their name
		      System.out.print("\n\n\nEnter Queries here: ");
		 
		      //  open up standard input
		      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		 
		      String query = null;
		 
		      //  read the username from the command-line; need to use try/catch with the
		      //  readLine() method
		      
		      try
		      {
				query = br.readLine();
		      } catch (IOException e1) 
		      {
				e1.printStackTrace();
		      }
		      QueryMsgFromUser<Integer> queryMsgFromUser = 
		        	new QueryMsgFromUser<Integer>(this.getContextService().getMyID(), query);
		      // nioTransport.sendToID(0, queryMesg.getJSONMessage());
		         
		      GenericMessagingTask<Integer, QueryMsgFromUser<Integer>> mtask = 
		    		  new GenericMessagingTask<Integer, QueryMsgFromUser<Integer>>
		      (this.getContextService().getMyID(), queryMsgFromUser);
					
		      // send the message 
		      try 
		      {
		    	  this.getContextService().getJSONMessenger().send(mtask);
		      } catch (JSONException e) 
		      {
		    	  e.printStackTrace();
		      } catch (IOException e) 
		      {
				e.printStackTrace();
		      }
		}
		
		//System.out.println("Thanks for the name, " + userName);
		//GNSCalls.clearNotificationSetOfAGroup(query);
		//GNSCalls.updateNotificationSetOfAGroup((InetSocketAddress)server_socket.getLocalSocketAddress(), query);
		//recvNotification(query);
	}*/
	
	
	public static void recvNotification(String query) 
    {
	   byte[] receive_data = new byte[1024];
       
       System.out.println ("UDPServer Waiting for client");
       
       
       while(true)
       {
    	   DatagramPacket receive_packet = new DatagramPacket(receive_data,
                                            receive_data.length);
    	   
    	   try 
    	   {
    		   server_socket.receive(receive_packet);
    	   } catch (IOException e) 
    	   {
    		   e.printStackTrace();
    	   }
           
    	   String data = new String(receive_packet.getData(),0, 0
                                         ,receive_packet.getLength());
                
                InetAddress IPAddress = receive_packet.getAddress();
                 
                System.out.println("\n\n"+data+"\n\n" );
                
                JSONArray res = GNSCalls.readGroupMembers(query);
    			if(res!=null)
    			{
    				System.out.println("\n\n query res "+ res);
    			} 
    			else
    			{
    				System.out.println("\n\n query res null" );
    			}
    	}
    }
	
	private static class StartNode implements Runnable
	{
		private final int nodeID;
		private final int myIndex;
		public StartNode(Integer givenNodeID, int index)
		{
			this.nodeID = givenNodeID;
			this.myIndex = index;
		}
		
		@Override
		public void run()
		{
			try
			{
				System.out.println("\n\nNode with id "+nodeID +" started\n\n");
				nodes[myIndex] = new BasicContextServiceExample(nodeID, csNodeConfig);
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
}