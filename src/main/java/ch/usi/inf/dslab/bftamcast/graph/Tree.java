/**
 * 
 */
package ch.usi.inf.dslab.bftamcast.graph;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import bftsmart.reconfiguration.util.HostsConfig.Config;

/**
 * @author Christian Vuerich - christian.vuerich@usi.ch
 *
 */
public class Tree {
	private Vertex root;
	private List<Integer> destinations;

	/**
	 * 
	 * @param vetices
	 *            list of vertices you are interest into knowing their lowest common
	 *            ancestor
	 * @return the lowest common ancestor in the tree of all the vertices in the
	 *         input vertices list.
	 */
	public Vertex lca(int[] vetices) {
		//TODO
		return null;
	}

	/**
	 * Main for testing
	 * @param args none
	 */
	public static void main(String[] args) {
		Tree t = new Tree("config/tree.conf");
		Vertex v = t.root;
	}

	/**
	 * getter for list of destinations in the tree
	 * 
	 * @return the field destinations containing the id of all destinations in the
	 *         tree
	 */
	public List<Integer> getDestinations() {
		return destinations;
	}

	/**
	 * Constructor
	 * 
	 * @param configFile
	 *            containing the id of the vertices and their config path for bft
	 *            smart and connection between them
	 */
	public Tree(String configFile) {
		destinations = new ArrayList();
		List<Vertex> vertices = new ArrayList<>();
		FileReader fr;
		BufferedReader rd;
		try {
			fr = new FileReader(configFile);
			rd = new BufferedReader(fr);
			String line = null;
			while ((line = rd.readLine()) != null) {
				System.out.println(line);
				if (!line.startsWith("#") && !line.isEmpty()) {
					// TODO instead of reading nodes and then tree, read nodes and specs (througput
					// etc and build optimal tree)
					StringTokenizer str = new StringTokenizer(line, " ");
					if (str.countTokens() == 2) {
						// throw away in config file (temporary to distinguish between vertex
						// declaration and edges)
						vertices.add(new Vertex(Integer.valueOf(str.nextToken()), str.nextToken()));
						destinations.add(vertices.get(vertices.size() - 1).groupId);
					}
					if (str.countTokens() == 3) {

						int from = Integer.valueOf(str.nextToken());
						str.nextToken();// throw away "->"
						int to = Integer.valueOf(str.nextToken());

						for (Vertex v1 : vertices) {
							if (v1.groupId == from) {
								for (Vertex v2 : vertices) {
									if (v2.groupId == to) {
										v1.connections.add(v2);
										v2.parent = v1;
									}
								}
							}
						}

						for (Vertex v : vertices) {
							if (v.parent == null) {
								root = v;
							}
						}
					}
				}
			}

			fr.close();
			rd.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Vertex findVertexById(int id) {
		return root.findVertexByID(id);
	}

}
