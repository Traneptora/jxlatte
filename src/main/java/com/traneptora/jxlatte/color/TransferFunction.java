package com.traneptora.jxlatte.color;

import com.traneptora.jxlatte.util.functional.FloatUnaryOperator;

public interface TransferFunction {

    public static TransferFunction TF_LINEAR = new TransferFunction() {
        @Override
        public double toLinear(double input) {
            return input;
        }

        @Override
        public double fromLinear(double input) {
            return input;
        }

        @Override
        public float toLinearF(float input) {
            return input;
        }

        @Override
        public float fromLinearF(float input) {
            return input;
        }
    };

    public static TransferFunction TF_SRGB = new TransferFunction() {
        @Override
        public double fromLinear(double f) {
            if (f < 0.00313066844250063D)
                return f * 12.92D;
            else
                return 1.055D * Math.pow(f, 0.4166666666666667D) - 0.055D;
        }

        @Override
        public float fromLinearF(float f) {
            if (f < 0.00313066844250063f)
                return f * 12.92f;
            else
                return 1.055f * (float)Math.pow(f, 0.4166666666666667D) + -0.055f;
        }

        @Override
        public double toLinear(double f) {
            if (f < 0.0404482362771082D)
                return f * 0.07739938080495357D;
            else
                return Math.pow((f + 0.055D) * 0.9478672985781991D, 2.4D);
        }

        @Override
        public float toLinearF(float f) {
            if (f < 0.0404482362771082f)
                return f * 0.07739938080495357f;
            else
                return (float)Math.pow(f * 0.9478672985781991f + 0.052132701f, 2.4D);
        }
    };

    public static TransferFunction TF_BT709 = new TransferFunction() {
        @Override
        public double fromLinear(double f) {
            if (f < 0.018053968510807807336D)
                return 4.5D * f;
            else
                return 1.0992968268094429403D * Math.pow(f, 0.45D) - 0.0992968268094429403D;
        }

        @Override
        public double toLinear(double f) {
            if (f < 0.081242858298635133011D)
                return f * 0.22222222222222222222D;
            else
                return Math.pow((f + 0.0992968268094429403D) * 0.90967241568627260377D, 2.2222222222222222222D);
        }
    };

    public static TransferFunction TF_PQ = new TransferFunction() {
        @Override
        public double fromLinear(double f) {
            double d = Math.pow(f, 0.159423828125D);
            return Math.pow((0.8359375D + 18.8515625D * d) / (1D + 18.6875D * d), 78.84375D);
        }

        @Override
        public double toLinear(double f) {
            double d = Math.pow(f, 0.012683313515655965121D);
            return Math.pow((d - 0.8359375D) / (18.8515625D + 18.6875D * d), 6.2725880551301684533D);
        }
    };

    public static TransferFunction TF_DCI = new GammaTransferFunction(3846154);

    public double toLinear(double input);
    public double fromLinear(double linear);

    public default float toLinearF(float input) {
        return (float)toLinear(input);
    }

    public default float fromLinearF(float input) {
        return (float)fromLinear(input);
    }

    public default FloatUnaryOperator toLinearFloatOp() {
        return f -> toLinearF(f);
    }

    public default FloatUnaryOperator fromLinearFloatOp() {
        return f-> fromLinearF(f);
    }
}
