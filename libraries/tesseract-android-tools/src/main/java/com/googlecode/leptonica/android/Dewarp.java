package com.googlecode.leptonica.android;

public class Dewarp {

	public static final int DEFAULT_SAMPLING = 0;
	
	static {
        System.loadLibrary("lept");
    }
	
	public static Pix dewarp(Pix pixs, int pageno, int sampling, int minlines, boolean applyhoriz) {
		if (pixs == null)
            throw new IllegalArgumentException("Source pix must be non-null");
		
		int nativePix = nativeDewarp(pixs.mNativePix, pageno, sampling, minlines, applyhoriz ? 1 : 0);
		
		if (nativePix == 0) {
			System.err.println("Failed to natively dewarp pix: " + nativePix);
			return pixs.clone();
            //throw new RuntimeException("Failed to natively dewarp pix: " + nativePix);
		}

        return new Pix(nativePix);
	}
	
	private static native int nativeDewarp(int nativePix, int pageno, int sampling, int minlines, int applyhoriz);
	
}
