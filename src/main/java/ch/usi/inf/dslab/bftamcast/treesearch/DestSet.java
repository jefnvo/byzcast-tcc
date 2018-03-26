/**
 * 
 */
package ch.usi.inf.dslab.bftamcast.treesearch;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Christian Vuerich - christian.vuerich@usi.ch
 *
 */
public class DestSet {
	public List<Vertex> destinations = new ArrayList<>();
	public List<Integer> destinationsIDS = new ArrayList<>();
	public int percentage;
	public boolean handled = false;

	public Vertex root;
	public List<DestSet> overlaps = new ArrayList<>();


	public DestSet(int percentage, List<Vertex> dests) {
		this.percentage = percentage;
		this.destinations =  dests;
		for (Vertex vertex : dests) {
			destinationsIDS.add(vertex.ID);
		}
		
	}

	public boolean matchDests(List<Integer> dests) {
		for (Integer i : destinationsIDS) {
			if (!dests.contains(i)) {
				return false;
			}
		}
		for (Integer i : dests) {
			if (!destinationsIDS.contains(i)) {
				return false;
			}
		}
		return true;
	}
}