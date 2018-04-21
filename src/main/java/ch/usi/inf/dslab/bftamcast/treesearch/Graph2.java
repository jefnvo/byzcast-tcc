/**
 * 
 */
package ch.usi.inf.dslab.bftamcast.treesearch;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author Christian Vuerich - christian.vuerich@usi.ch
 *
 */
public class Graph2 {
	public List<Vertex> vertices = new ArrayList<>();
	public List<Edge> edges = new ArrayList<>();
	public List<DestSet> load = new ArrayList<>();
	public int numerOfReplicas = 4;
	public static BigInteger bestbestscore = BigInteger.ZERO;

	// instead have a sorter for each destination combo
	public static void main(String[] args) throws Exception {
		new Graph2("config/load.conf");
	}

	public Graph2(String configFile) throws Exception {

		// parse edges, vertices, load and constrains
		FileReader fr;
		BufferedReader rd = null;

		try {
			fr = new FileReader(configFile);

			rd = new BufferedReader(fr);
			String line = null;
			while ((line = rd.readLine()) != null) {
				// comment line
				if (!line.startsWith("#") && !line.isEmpty()) {
					StringTokenizer str;
					// load line
					if (line.contains("m/s")) {
						str = new StringTokenizer(line, "m/s");
						if (str.countTokens() == 2) {
							int loadp = Integer.valueOf(str.nextToken());
							Set<Vertex> ver = new HashSet<>();
							str = new StringTokenizer(str.nextToken(), " ");
							while (str.hasMoreTokens()) {
								int id = Integer.valueOf(str.nextToken());
								for (Vertex v : vertices) {
									if (v.getID() == id) {
										ver.add(v);
										break;
									}
								}
							}
							// create destination load
							DestSet s = new DestSet(loadp, ver);
							load.add(s);
						}
					} else {
						// vertex declaration (group)
						str = new StringTokenizer(line, " ");
						if (str.countTokens() == 3) {
							Vertex v = new Vertex(Integer.valueOf(str.nextToken()), str.nextToken(),
									Integer.valueOf(str.nextToken()), numerOfReplicas);
							vertices.add(v);
						}
						// edge declaration latency
						else if (str.countTokens() == 4) {
							int a = Integer.valueOf(str.nextToken());
							str.nextToken(); // drop "-"
							int b = Integer.valueOf(str.nextToken());
							int latency = Integer.valueOf(str.nextToken());
							Vertex aa = null, bb = null;
							for (Vertex v : vertices) {
								if (a == v.getID()) {
									aa = v;
								}
								if (b == v.getID()) {
									bb = v;
								}
							}
							if (aa == null || bb == null) {
								System.err.println("connection not know for edge");
								return;
							}
							// edges are bidirectional
							Edge e1 = new Edge(aa, bb, latency);
							Edge e2 = new Edge(bb, aa, latency);
							aa.addEdge(e1);
							bb.addEdge(e2);

							edges.add(e1);
							edges.add(e2);
						}

					}
				}

			}
			fr.close();
			rd.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (rd != null) {
				try {
					rd.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}

		// testing generate tree

		// int ogsize = vertices.size();
		// for (int i = vertices.size()-1; i < 4; i++) {
		// vertices.add(new Vertex(i, "", 100000, numerOfReplicas));
		// }
		for (Vertex v1 : vertices) {
			for (Vertex v2 : vertices) {
				v1.setCapacity(100000);
				v1.setResCapacity(100000);
				v2.setCapacity(100000);
				v2.setResCapacity(100000);
				if (v1 != v2) {
					Edge e = new Edge(v1, v2, 100);
					v1.addEdge(e);
					edges.add(e);
				}
			}
		}
		System.out.println("done generating test");

		// generate all possible destinations, not all might be specified, and assign
		// base load (1m/s)ß
		int baseload = 1;
		// generate all dests and add not specified ones
		Set<Set<Vertex>> allDests = getAlldestinations(vertices);
		System.out.println("done generating dests");
		Random r = new Random();
		// for(List<Vertex> f : allDests) {
		// for (Vertex v : f) {
		// System.out.print(v.ID+ " ,");
		// }
		// System.out.println();
		// }
		for (DestSet d : load) {
			Set<Vertex> toremove = null;
			for (Set<Vertex> f : allDests) {
				if (f.containsAll(d.destinations)) {
					toremove = f;
					// System.out.println("fasljdfdksajfkljadslkfjladskfjlask");
					break;
				}
			}
			allDests.remove(toremove);
		}

		for (Set<Vertex> d : allDests) {
			// if (!existsLoad(d)) {

			// load.add(new DestSet(r.nextInt(5)+1, d));
			load.add(new DestSet(baseload, d));
			// }
		}
		System.out.println("sets dest size = " + load.size());
		System.out.println("vert size = " + vertices.size());
		System.out.println("edges size = " + edges.size());

		load.sort(new DestSet(0, null));


		List<List<Edge>> trees = new ArrayList<>();

		generatetrees(vertices, trees);

		System.out.println("#generated trees are:  " + trees.size() + "  expected: " + ((long) numtree) + "   total iterations: " + iteration );

	}

	public void generatetrees(List<Vertex> vertices, List<List<Edge>> trees) throws Exception {
		// TODO recycle all destination when generating all loads initialy
		Set<Set<Vertex>> sets = getAlldestinations(vertices);
		time = System.nanoTime();
		double n = vertices.size();
		numtree = Math.pow(n, n - 1);
		for (Vertex root : vertices) {
			List<Vertex> vi = new ArrayList<>();
			Set<Set<Vertex>> setsToUse = new HashSet<>();
			for (Set<Vertex> set : sets) {
				if (!set.contains(root)) {
					setsToUse.add(set);
				}
			}
			vi.add(root);
			List<Vertex> fr1 = new ArrayList<>();
			fr1.add(root);
			gen(new ArrayList<>(), vi, trees, vertices.size(), setsToUse, fr1);
		}
	}

	public static int iteration = 0;
	public static long time = 0;
	public static double numtree = 0;
	public static DecimalFormat myFormat = new DecimalFormat("0.00000000");

	// GOOD algorithm! works no dups, tested up to 8 vertices, generates all trees
	// n^(n-1)
	// TODO move to graph.java and re-add pruning + core computation
	public void gen(List<Edge> tree, List<Vertex> visited, List<List<Edge>> trees, int numVertices,
			Set<Set<Vertex>> sets, List<Vertex> fringe) throws Exception {

		iteration++;

		if (tree.size() == numVertices - 1) {
			trees.add(new ArrayList<>(tree));
			if (System.nanoTime() - time >= 1 * 1e9) {
				System.out.println(myFormat.format((((((double) trees.size()) / numtree) * 100))) + "%");
				time = System.nanoTime();
			}
			return;
		}

		List<Vertex> nnf = new ArrayList<>(fringe);

		for (Vertex v : fringe) {
			nnf.remove(v);
			for (Set<Vertex> chance : sets) {
				//TODO this assume node v can reach every other node(they all have the same possible children), if not connected keep possible childs in

				List<Vertex> nv = new ArrayList<>(visited);
				nv.addAll(chance);

				List<Vertex> nf = new ArrayList<>(nnf);
				nf.remove(v);

				List<Edge> nt = new ArrayList<>(tree);

				Set<Set<Vertex>> nsets = new HashSet<>(sets);
				Set<Set<Vertex>> toremove = new HashSet<>();

				// System.out.print("adding to: " + v.ID + " vertices : ");
				for (Edge e : v.getOutgoingEdges()) {
					if (chance.contains(e.to)) {
						// System.out.print(e.to.ID + " ");
						nf.add(e.to);
						nt.add(e);
						for (Set<Vertex> set : nsets) {
							if (set.contains(e.to)) {
								toremove.add(set);
							}
						}
						nsets.removeAll(toremove);
						toremove.clear();
					}
				}
				// System.out.println();

				gen(nt, nv, trees, numVertices, nsets, nf);

			}
			// }
		}
		// System.out.println("iteration " + iteration + "done");
	}

	// // generate all possible desitations
	public static Set<Set<Vertex>> getAlldestinations(List<Vertex> vertices) {
		Set<Set<Vertex>> destinations = new HashSet<>();
		getgetAlldestinations2(vertices, 0, destinations, new HashSet<>());
		return destinations;
	}

	private static void getgetAlldestinations2(List<Vertex> vertices, int index, Set<Set<Vertex>> destinations,
			Set<Vertex> previous) {
		if (index >= vertices.size()) {
			return;
		}
		previous.add(vertices.get(index));
		destinations.add(new HashSet<>(previous));
		// consider vertex
		getgetAlldestinations2(vertices, index + 1, destinations, previous);
		// skip vertex
		previous.remove(vertices.get(index));
		getgetAlldestinations2(vertices, index + 1, destinations, previous);
	}
}
