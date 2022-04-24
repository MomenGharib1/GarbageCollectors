package com.company.GarbageCollectors;

import com.company.HeapConstructor;
import com.company.ObjectInfo;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Arrays;
import java.util.Comparator;

public class G1GC {
	private static final int numOfRegions = 16;
	static private HashMap<Integer, Integer> regionIndex = new HashMap<>();
	static PriorityQueue<ObjectInfo> activeObjects;
	static private int regionSize;
	static int availableSpaceInEachRegion[];
	static int numOfObjectsInEachRegion[];

	public static void main(String[] args) throws Exception {
		HashMap<Integer, ObjectInfo> heap;
		ArrayList<ObjectInfo> roots = new ArrayList<>();
		FileWriter destinationFile;
		activeObjects = new PriorityQueue<>(2, new Comparator<ObjectInfo>() {
			@Override
			public int compare(ObjectInfo o1, ObjectInfo o2) {
				if (o1.getMemStart() > o2.getMemStart())
					return 1;
				else
					return -1;
			}
		});
		boolean isOccupiedRegion[] = new boolean[numOfRegions + 1];
		try {
			int totalSize = Integer.parseInt(args[0]);
			regionSize = totalSize / numOfRegions;
			availableSpaceInEachRegion = new int[numOfRegions + 1];
			numOfObjectsInEachRegion = new int[numOfRegions + 1];
			String[] files = Arrays.copyOfRange(args, 1, args.length);
			HeapConstructor heapConstructor = new HeapConstructor(files);
			heap = heapConstructor.getHeap();

			for (int id : heapConstructor.getRoots()) {
				roots.add(heap.get(id));
			}
			destinationFile = heapConstructor.getDestinationFile("G1GC.csv");
			setRegions(heap);
			markAndSweep(heap, roots, isOccupiedRegion);
			heap = defragment(heap);
			writeOut(destinationFile, heap);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
		System.out.println("Done G1 GC");
	}

	private static HashMap<Integer, ObjectInfo> defragment(HashMap<Integer, ObjectInfo> heap) {
		heap = new HashMap<>();
		int startIndex = getStartIndex();
		int currentIndex = startIndex;
		while (!activeObjects.isEmpty()) {
			if (currentIndex > numOfRegions) {
				currentIndex = 1;
			}
			ObjectInfo obj = activeObjects.peek();
			int regionNumber = getObjectRegion(obj.getMemStart());
			int objSize = obj.getSize();
			if (objSize <= availableSpaceInEachRegion[currentIndex] && regionNumber != currentIndex) {
				moveObject(obj, availableSpaceInEachRegion, regionNumber, currentIndex, regionSize);
				currentIndex = startIndex;
				ObjectInfo active = activeObjects.remove();
				heap.put(active.getId(), active);
				continue;
			}
			currentIndex++;
		}
		return heap;
	}

	private static int getStartIndex() {
		int start = 1;
		for (int i = 1; i < numOfRegions; i++) {
			if (availableSpaceInEachRegion[i] == regionSize) {
				start = i;
				break;
			}
		}
		return start;
	}

	private static void writeOut(FileWriter destinationFile, HashMap<Integer, ObjectInfo> heap) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (int id : heap.keySet()) {
			sb.append(heap.get(id).toCSVLine());
		}
		destinationFile.write(sb.toString());
		destinationFile.close();
	}

	private static void moveObject(ObjectInfo obj, int[] regions, int regionNumber, int currentIndex, int regionSize) {
		int newStartMem = (currentIndex - 1) * regionSize + (regionSize - regions[currentIndex]);
		obj.move(newStartMem);
		numOfObjectsInEachRegion[regionNumber]--;
		numOfObjectsInEachRegion[currentIndex]++;
		regions[regionNumber] += obj.getSize();
		regions[currentIndex] -= obj.getSize();
		if (numOfObjectsInEachRegion[regionNumber] == 0) {
			regions[regionNumber] = regionSize;
		}
	}

	private static void setRegions(HashMap<Integer, ObjectInfo> heap) {
		Arrays.fill(availableSpaceInEachRegion, regionSize);
		for (ObjectInfo object : heap.values()) {
			int region = getObjectRegion(object.getMemStart());
			regionIndex.put(object.getId(), region);
			availableSpaceInEachRegion[region] -= object.getSize();
		}
	}

	private static int getObjectRegion(int memStart) {
		int regionNum = (int) Math.ceil((double) memStart / regionSize);
		if (memStart % regionSize == 0) {
			regionNum++;
		}
		return regionNum;
	}

	private static void markAndSweep(HashMap<Integer, ObjectInfo> heap, ArrayList<ObjectInfo> roots,
			boolean[] isOccupiedRegion) {
		mark(roots, isOccupiedRegion);
		sweep(heap, isOccupiedRegion);
	}

	public static void mark(ArrayList<ObjectInfo> roots, boolean[] isOccupiedRegion) {
		for (ObjectInfo root : roots) {
			if (root.isMarked()) {
				continue;
			}
			root.setMarked();
			isOccupiedRegion[regionIndex.get(root.getId())] = true;
			mark(root.getRef(), isOccupiedRegion);
		}
	}

	public static void sweep(HashMap<Integer, ObjectInfo> heap, boolean[] isOccupiedRegion) {
		ArrayList<Integer> unmarkedObjects = new ArrayList<>();
		for (ObjectInfo object : heap.values()) {
			int region = regionIndex.get(object.getId());
			if (!isOccupiedRegion[region]) {
				unmarkedObjects.add(object.getId());
				availableSpaceInEachRegion[region] = regionSize;
			} else if (object.isMarked()) {
				activeObjects.add(object);
				numOfObjectsInEachRegion[region]++;
			}
		}
		for (int unmarkedObject : unmarkedObjects) {
			heap.remove(unmarkedObject);
		}
	}
}
