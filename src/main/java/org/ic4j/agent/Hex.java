package org.ic4j.agent;

import javax.xml.bind.DatatypeConverter;

public class Hex {
    // Convert hexadecimal string to byte array
    public static byte[] decodeHex(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hexadecimal string must have an even length");
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                  + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }	
    
    // Convert byte array to hexadecimal string
    public static String encodeHexString(byte[] bytes) {
        return DatatypeConverter.printHexBinary(bytes).toLowerCase();
    }    
}
