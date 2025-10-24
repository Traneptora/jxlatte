package com.traneptora.jxlatte.frame.modular;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.io.Loggers;
import com.traneptora.jxlatte.util.MathHelper;

public class MATree {
    private EntropyStream stream;

    private MATree leftChildNode;
    private MATree rightChildNode;

    private int property = -1;
    private int value;
    private int leftChildIndex;
    private int rightChildIndex;

    private int context;
    private int predictor = -1;
    private int offset;
    private int multiplier;

    private MATree() {

    }

    public MATree(Loggers loggers, Bitreader reader) throws IOException {
        List<MATree> nodes = new ArrayList<>();
        EntropyStream stream = new EntropyStream(loggers, reader, 6);
        int contextId = 0;
        int nodesRemaining = 1;
        while (nodesRemaining-- > 0) {
            if (nodes.size() > (1 << 20))
                throw new InvalidBitstreamException("Tree too large");
            int property = stream.readSymbol(reader, 1) - 1;
            MATree node = nodes.size() == 0 ? this : new MATree();
            if (property >= 0) {
                int value = MathHelper.unpackSigned(stream.readSymbol(reader, 0));
                int leftChild = nodes.size() + nodesRemaining + 1;
                node.property = property;
                node.predictor = -1;
                node.value = value;
                node.leftChildIndex = leftChild;
                node.rightChildIndex = leftChild + 1;
                nodes.add(node);
                nodesRemaining += 2;
            } else {
                int context = contextId++;
                int predictor = stream.readSymbol(reader, 2);
                if (predictor > 13)
                    throw new InvalidBitstreamException("Invalid predictor value");
                int offset = MathHelper.unpackSigned(stream.readSymbol(reader, 3));
                int mulLog = stream.readSymbol(reader, 4);
                if (mulLog > 30)
                    throw new InvalidBitstreamException("MulLog too large");
                int mulBits = stream.readSymbol(reader, 5);
                if (mulBits > (1 << (31 - mulLog)) - 2)
                    throw new InvalidBitstreamException("MulBits too large");
                int multiplier = (mulBits + 1) << mulLog;
                node.context = context;
                node.predictor = predictor;
                node.multiplier = multiplier;
                node.offset = offset;
                node.property = -1;
                nodes.add(node);
            }
        }
        if (!stream.validateFinalState())
            throw new InvalidBitstreamException("Illegal MA Tree Entropy Stream");

        this.stream = new EntropyStream(loggers, reader, (nodes.size() + 1) / 2);

        for (int n = 0; n < nodes.size(); n++) {
            MATree node = nodes.get(n);
            node.stream = this.stream;
            if (!node.isLeafNode()) {
                node.leftChildNode = nodes.get(node.leftChildIndex);
                node.rightChildNode = nodes.get(node.rightChildIndex);
            }
        }
    }

    public boolean isLeafNode() {
        return property < 0;
    }

    public boolean usesWeightedPredictor() {
        if (isLeafNode())
            return predictor == 6; // weighted predictor
        else
            return property == 15 // max error
                || leftChildNode.usesWeightedPredictor()
                || rightChildNode.usesWeightedPredictor();
    }

    public MATree compactify(int channelIndex, int streamIndex) {
        return compactify(channelIndex, streamIndex, -1);
    }

    public MATree compactify(int channelIndex, int streamIndex, int y) {
        if (this.isLeafNode())
            return this;
        int prop;
        switch (property) {
            case 0:
                prop = channelIndex;
                break;
            case 1:
                prop = streamIndex;
                break;
            case 2:
                prop = y;
                break;
            default:
                prop = -1;
                break;
        }
        if (prop >= 0) {
            MATree branch = prop > value ? leftChildNode : rightChildNode;
            return branch.compactify(channelIndex, streamIndex, y);
        } else {
            MATree tree = new MATree();
            tree.property = this.property;
            tree.value = this.value;
            tree.leftChildNode = this.leftChildNode.compactify(channelIndex, streamIndex, y);
            tree.rightChildNode = this.rightChildNode.compactify(channelIndex, streamIndex, y);
            return tree;
        }
    }

    public MATree walk(IntUnaryOperator property) {
        if (isLeafNode())
            return this;
        MATree branch = property.applyAsInt(this.property) > value ? leftChildNode : rightChildNode;
        return branch.walk(property);
    }

    public int getSize() {
        if (isLeafNode())
            return 1;
        return 1 + leftChildNode.getSize() + rightChildNode.getSize();
    }

    private void validateLeaf() throws IllegalStateException {
        if (!isLeafNode())
            throw new IllegalStateException("Not a leaf node");
    }

    public int getContext() {
        validateLeaf();
        return context;
    }

    public int getPredictor() {
        validateLeaf();
        return predictor;
    }

    public int getMultiplier() {
        validateLeaf();
        return multiplier;
    }

    public int getOffset() {
        validateLeaf();
        return offset;
    }

    public EntropyStream getStream() {
        return stream;
    }

    public String toString() {
        if (isLeafNode()) {
            return String.format("{\"context\": %d, \"predictor\": %d, \"offset\": %d, \"multiplier\": %d}", context, predictor,
                offset, multiplier);
        } else {
            return String.format("{\"property\": %d, \"value\": %d, \"left\": %s, \"right\": %s}", property, value,
                leftChildNode.toString(), rightChildNode.toString());
        }
    }
}
