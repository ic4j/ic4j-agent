package org.ic4j.agent.certification.hashtree;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.ic4j.agent.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HashTreeTest {
	@Test
	void computesProtocolDigestsForEveryNodeType() {
		HashTree empty = new HashTree(new EmptyHashTreeNode());
		HashTree leaf = new HashTree(new LeafHashTreeNode(bytes("value")));
		HashTree pruned = new HashTree(new PrunedHashTreeNode(range(32)));
		HashTree fork = new HashTree(new ForkHashTreeNode(new EmptyHashTreeNode(), new LeafHashTreeNode(bytes("value"))));
		HashTree labeled = new HashTree(new LabeledHashTreeNode(new Label("label"),
				new LeafHashTreeNode(bytes("value"))));

		Assertions.assertEquals("4e3ed35c4e2d1ee89996483fb6260a64cffb6c47dbab216e7930e82f8190d120",
				Hex.encodeHexString(empty.digest()));
		Assertions.assertEquals("6a2bf0b271bb2e18639ff6cba9b1cec51a3e15cec33015091bd1c54a2ddd0c01",
				Hex.encodeHexString(leaf.digest()));
		Assertions.assertEquals("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
				Hex.encodeHexString(pruned.digest()));
		Assertions.assertEquals("fc50c407d0d70f00db1c1bc9278673f6dc0a4673beffc776ac00b725d7f8f6ee",
				Hex.encodeHexString(fork.digest()));
		Assertions.assertEquals("fad52aa387544ef7de4fba7fa289e66465b65bb355b1588f8ba81fee5227da07",
				Hex.encodeHexString(labeled.digest()));
	}

	@Test
	void findsNestedValuesAcrossForksAndEnumeratesPaths() {
		HashTree tree = new HashTree(new LabeledHashTreeNode(new Label("root"),
				new ForkHashTreeNode(
						new LabeledHashTreeNode(new Label("alpha"), new LeafHashTreeNode(bytes("a"))),
						new LabeledHashTreeNode(new Label("beta"), new LeafHashTreeNode(bytes("b"))))));

		LookupResult lookup = tree.lookupPath(labels("root", "beta"));
		Assertions.assertEquals(LookupResult.LookupResultStatus.FOUND, lookup.status);
		Assertions.assertArrayEquals(bytes("b"), lookup.value);

		List<List<Label>> paths = tree.listPaths();
		Assertions.assertEquals(2, paths.size());
		Assertions.assertEquals(Arrays.asList("root", "alpha"), strings(paths.get(0)));
		Assertions.assertEquals(Arrays.asList("root", "beta"), strings(paths.get(1)));
	}

	@Test
	void distinguishesAbsentUnknownAndInvalidPaths() {
		HashTree complete = new HashTree(new LabeledHashTreeNode(new Label("present"),
				new LeafHashTreeNode(bytes("value"))));
		HashTree pruned = new HashTree(new PrunedHashTreeNode(new byte[32]));
		HashTree fork = new HashTree(new ForkHashTreeNode(new EmptyHashTreeNode(), new EmptyHashTreeNode()));

		Assertions.assertEquals(LookupResult.LookupResultStatus.ABSENT,
				complete.lookupPath(labels("missing")).status);
		Assertions.assertEquals(LookupResult.LookupResultStatus.UNKNOWN,
				pruned.lookupPath(labels("missing")).status);
		Assertions.assertEquals(LookupResult.LookupResultStatus.ERROR,
				fork.lookupPath(Collections.emptyList()).status);
		Assertions.assertEquals(LookupResult.LookupResultStatus.ERROR,
				complete.lookupPath(Collections.emptyList()).status);
	}

	@Test
	void returnsFoundAbsentAndUnknownSubtrees() {
		HashTree tree = new HashTree(new LabeledHashTreeNode(new Label("root"),
				new LabeledHashTreeNode(new Label("child"), new LeafHashTreeNode(bytes("value")))));
		HashTree pruned = new HashTree(new PrunedHashTreeNode(new byte[32]));
		HashTree complete = new HashTree(new LabeledHashTreeNode(new Label("known"), new EmptyHashTreeNode()));

		SubtreeLookupResult found = tree.lookupSubtree(labels("root"));
		Assertions.assertEquals(SubtreeLookupResult.SubtreeLookupResultStatus.FOUND, found.status);
		Assertions.assertEquals(LookupResult.LookupResultStatus.FOUND,
				found.value.lookupPath(labels("child")).status);
		Assertions.assertEquals(SubtreeLookupResult.SubtreeLookupResultStatus.ABSENT,
				complete.lookupSubtree(labels("missing")).status);
		Assertions.assertEquals(SubtreeLookupResult.SubtreeLookupResultStatus.UNKNOWN,
				pruned.lookupSubtree(labels("missing")).status);
	}

	private static List<Label> labels(String... values) {
		List<Label> labels = new ArrayList<>();
		for (String value : values)
			labels.add(new Label(value));
		return labels;
	}

	private static List<String> strings(List<Label> labels) {
		List<String> values = new ArrayList<>();
		for (Label label : labels)
			values.add(label.toString());
		return values;
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] range(int length) {
		byte[] value = new byte[length];
		for (int i = 0; i < length; i++)
			value[i] = (byte) i;
		return value;
	}
}
