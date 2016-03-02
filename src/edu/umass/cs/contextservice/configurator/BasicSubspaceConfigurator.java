package edu.umass.cs.contextservice.configurator;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import edu.umass.cs.contextservice.attributeInfo.AttributeMetaInfo;
import edu.umass.cs.contextservice.attributeInfo.AttributeTypes;
import edu.umass.cs.contextservice.common.CSNodeConfig;
import edu.umass.cs.contextservice.config.ContextServiceConfig;
import edu.umass.cs.contextservice.hyperspace.storage.AttributePartitionInfo;
import edu.umass.cs.contextservice.hyperspace.storage.SubspaceInfo;
import edu.umass.cs.contextservice.logging.ContextServiceLogger;
import edu.umass.cs.nio.interfaces.NodeConfig;

/**
 * Basic subspace configurator partitions attributes into
 * subspaces and assigns each subspace to all available nodes.
 * @author adipc
 */
public class BasicSubspaceConfigurator<NodeIDType> extends AbstractSubspaceConfigurator<NodeIDType>
{
	private final double optimalH;
	
	public BasicSubspaceConfigurator(NodeConfig<NodeIDType> nodeConfig, int optimalH)
	{
		super(nodeConfig);
		this.optimalH = optimalH;
	}
	
	@Override
	public void configureSubspaceInfo()
	{
		double numNodes = nodeConfig.getNodeIDs().size();
		double numAttrs = AttributeTypes.attributeMap.size();
		ContextServiceLogger.getLogger().fine("double divice "+numAttrs/optimalH);
		double numSubspaces = Math.ceil(numAttrs/optimalH);
		
		Vector<AttributeMetaInfo> attrVector 
				= new Vector<AttributeMetaInfo>();

		attrVector.addAll(AttributeTypes.attributeMap.values());
		// handling the case if somehow numSubspaces 
		// becomes greater than numNodes, specially in 1 node case
//		if(numSubspaces > numNodes)
//		{
//			numSubspaces = numNodes;
//			optimalH = Math.ceil(numAttrs/numSubspaces);
//		}

		double numberNodesForSubspace = Math.floor(numNodes/numSubspaces);
		
		if(numberNodesForSubspace > 0)
		{
			// first the basic nodes are assigned then remaining nodes are assigned 
			// uniformly to the existing subspaces.
	
			int nodesIdCounter = assignNodesUniformlyToSubspaces(numberNodesForSubspace, (int)numSubspaces);
	
			//double remainingNodes = numNodes - numberNodesForSubspace*numSubspaces;
	
			Iterator<Integer> subspaceKeyIter = subspaceInfoMap.keySet().iterator();
	
			while( nodesIdCounter < numNodes )
			{
				int subspaceKey = -1;
				
				if( subspaceKeyIter.hasNext() )
				{
					subspaceKey = subspaceKeyIter.next();
					Vector<SubspaceInfo<NodeIDType>> currSubVect
										= subspaceInfoMap.get(subspaceKey);
					assert( currSubVect.size() > 0 );
					currSubVect.get(0).getNodesOfSubspace().add( (NodeIDType)(Integer)nodesIdCounter );
					nodesIdCounter++;
				}
				else
				{
					subspaceKeyIter = subspaceInfoMap.keySet().iterator();
				}
			}
		}
		else // numnodes are less than number of subspaces, so just assign each node to each subspace
		{
			// first the basic nodes are assigned then remaining nodes are assigned 
			// uniformly to the existing subspaces.
				
			int nodesIdCounter = assignNodesUniformlyToSubspaces(numberNodesForSubspace, (int)numSubspaces);
			// above will not be able to assign any nodes
			assert(nodesIdCounter == 0);
			
			Iterator<Integer> subspaceKeyIter = subspaceInfoMap.keySet().iterator();
	
			while( nodesIdCounter < numNodes )
			{
				int subspaceKey = -1;
				// assigning each node to each subspace
				while( subspaceKeyIter.hasNext() )
				{
					subspaceKey = subspaceKeyIter.next();
					Vector<SubspaceInfo<NodeIDType>> currSubVect
										= subspaceInfoMap.get(subspaceKey);
					assert( currSubVect.size() > 0 );
					currSubVect.get(0).getNodesOfSubspace().add( (NodeIDType)(Integer)nodesIdCounter );
				}
				nodesIdCounter++;
			}
		}
		// initializes the domain partitions for an attribute
		initializePartitionInfo();
		this.printSubspaceInfo();
	}
	
	/**
	 * This function assigns nodes uniformly to subspaces
	 * and stores them in the inherited subspaceInfoVector 
	 * vector.
	 * returns the nodeId of the first unassigned node
	 * nodeId <= numNodes
	 */
	private int assignNodesUniformlyToSubspaces
			(double numberNodesForSubspace, int numSubspaces)
	{
		Vector<AttributeMetaInfo> attrVector = new Vector<AttributeMetaInfo>();
		attrVector.addAll(AttributeTypes.attributeMap.values());
		
		//double numberNodesForSubspace = Math.floor(numNodes/numSubspaces);
		
		// first the basic nodes are assigned then remaining nodes are assigned 
		// uniformly to the existing subspaces.
		
		int nodesIdCounter   = 0;
		int attrIndexCounter = 0;
		
		for(int i=0; i<numSubspaces; i++)
		{
			int distinctSubspaceId 	= i;
			
			Vector<NodeIDType> subspaceNodes = new Vector<NodeIDType>();
			HashMap<String, AttributePartitionInfo> subspaceAttrs 
									= new HashMap<String, AttributePartitionInfo>();
			
			for(int j=0; j<numberNodesForSubspace; j++)
			{
				subspaceNodes.add( (NodeIDType)(Integer)nodesIdCounter );
				nodesIdCounter++;
			}
			
			int numCurrAttr = 0;
			
			while( (numCurrAttr < optimalH) && (attrIndexCounter < attrVector.size()) )
			{
				AttributeMetaInfo currAttrMetaInfo = attrVector.get(attrIndexCounter);
				String attrName = currAttrMetaInfo.getAttrName();
				AttributePartitionInfo attrPartInfo = new AttributePartitionInfo
						( currAttrMetaInfo );
				subspaceAttrs.put(attrName, attrPartInfo);
				numCurrAttr++;
				attrIndexCounter++;
			}
			
			// replica num 0 as first replica is created
			SubspaceInfo<NodeIDType> currSubInfo 
				= new SubspaceInfo<NodeIDType>( distinctSubspaceId, 0, subspaceAttrs, subspaceNodes );
			
			Vector<SubspaceInfo<NodeIDType>> replicatedSubspacesVector 
						= new Vector<SubspaceInfo<NodeIDType>>(); 
			replicatedSubspacesVector.add(currSubInfo);
			
			subspaceInfoMap.put(distinctSubspaceId, replicatedSubspacesVector);
		}
		//all attributes should be assigned to some subspace by end of this function.
		assert(attrIndexCounter == attrVector.size() );
		return nodesIdCounter;
	}
	
	public static void main(String[] args)
	{
		int numberOfNodes = Integer.parseInt(args[0]);
		
		ContextServiceConfig.configFileDirectory = "conf/singleNodeConf/contextServiceConf";
		AttributeTypes.initialize();
		
		CSNodeConfig<Integer> testNodeConfig = new CSNodeConfig<Integer>();
		int startingPort = 5000;
		
		for( int i=0;i<numberOfNodes;i++ )
		{
			InetSocketAddress sockAddr 
					= new InetSocketAddress("127.0.0.1", startingPort+i);
			testNodeConfig.add(i, sockAddr);
		}
		
		AbstractSubspaceConfigurator<Integer> basicSubspaceConfigurator 
								= new BasicSubspaceConfigurator<Integer>(testNodeConfig, 2);
		
		basicSubspaceConfigurator.configureSubspaceInfo();
		basicSubspaceConfigurator.printSubspaceInfo();
	}
}