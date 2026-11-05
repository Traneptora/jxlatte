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

    /*
     * as defined in SMPTE ST 2084 and Rec. ITU-R BT.2100
     */
    public static TransferFunction TF_PQ = new TransferFunction() {
        private static final double M1 = 2610D / 16384D;
        private static final double M2 = 2523D / 4096D * 128D;
        private static final double C1 = 3424D / 4096D;
        private static final double C2 = 2413D / 4096D * 32D;
        private static final double C3 = 2392D / 4096D * 32D;

        @Override
        public double fromLinear(double f) {
            double d = Math.pow(f, M1);
            return Math.pow((C1 + C2 * d) / (1D + C3 * d), M2);
        }

        @Override
        public double toLinear(double f) {
            double d = Math.pow(f, 1D / M2);
            return Math.pow(Math.max(d - C1, 0D) / (C2 - C3 * d), 1D / M1);
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
