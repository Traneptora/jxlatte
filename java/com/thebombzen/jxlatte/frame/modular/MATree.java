package com.thebombzen.jxlatte.frame.modular;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.io.Bitreader;

public class MATree {

    private List<MANode> nodes = new ArrayList<>();
    public final EntropyStream stream;

    public MATree(Bitreader reader) throws IOException {
        EntropyStream stream = new EntropyStream(reader, 6);
        int contextId = 0;
        int nodesRemaining = 1;
        while (nodesRemaining-- > 0) {
            int property = stream.readSymbol(reader, 1) - 1;
            if (property >= 0) {
                int value = MathHelper.unpackSigned(stream.readSymbol(reader, 0));
                int leftChild = nodes.size() + nodesRemaining + 1;
                MADecisionNode node = new MADecisionNode(property, value, leftChild, leftChild + 1);
                nodes.add(node);
                nodesRemaining += 2;
            } else {
                int context = contextId++;
                int predictor = stream.readSymbol(reader, 2);
                if (predictor > 13)
                    throw new InvalidBitstreamException("Invalid predictor value");
                int offset = MathHelper.unpackSigned(stream.readSymbol(reader, 3));
                int mulLog = stream.readSymbol(reader, 4);
                int mulBits = stream.readSymbol(reader, 5);
                int multiplier = (mulBits + 1) << mulLog;
                MALeafNode node = new MALeafNode(context, predictor, offset, multiplier);
                nodes.add(node);
            }
        }
        System.err.println(nodes.toString());
        this.stream = new EntropyStream(reader, (getSize() + 1) / 2);
    }

    public MALeafNode walk(IntUnaryOperator property) {
        int index = 0;
        while (true) {
            MANode node = nodes.get(index);
            if (node instanceof MALeafNode)
                return (MALeafNode)node;
            MADecisionNode dNode = (MADecisionNode)node;
            if (property.applyAsInt(dNode.property) > dNode.value)
                index = dNode.leftChildIndex;
            else
                index = dNode.rightChildIndex;
        }
    }

    public int getSize() {
        return nodes.size();
    }
}
