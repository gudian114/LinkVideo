package com.linkvideo.parser;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/** Java adaptation of the Apache-2.0 Douyin a_bogus reference.
 * See docs/third-party-notices.md for source and license. */
final class DouyinRequestSignature {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36";
    private static final String ALPHABET = "Dkdpgh2ZmsQB80/MfvV36XI1R45-WUAlEixNLwoqYTOPuzKFjJnry79HbGcaStCe";
    private static final String BROWSER = "1536|742|1536|864|0|0|0|0|1536|864|1536|864|1536|742|24|24|MacIntel";
    private static final int[] UA_HASH = {76,98,15,131,97,245,224,133,122,199,241,166,79,34,90,191,
            128,126,122,98,66,11,14,40,49,110,110,173,67,96,138,252};

    static String sign(String query) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long start = System.currentTimeMillis();
        return sign(query, start, start + random.nextInt(4, 9),
                new int[]{random.nextInt(1, 10000), random.nextInt(1, 10000), random.nextInt(1, 10000)});
    }

    static String sign(String query, long start, long end, int[] random) {
        byte[] paramsHash = sm3(sm3((query + "cus").getBytes(StandardCharsets.UTF_8)));
        byte[] methodHash = sm3(sm3("GETcus".getBytes(StandardCharsets.UTF_8)));
        int[] values = {44, at(end,24), 0,0,0,0,24, paramsHash[21]&255, methodHash[21]&255,0,
                UA_HASH[23],at(end,16),0,0,0,1,0,239,paramsHash[22]&255,methodHash[22]&255,
                UA_HASH[24],at(end,8),0,0,0,0,at(end,0),0,0,14,at(start,24),at(start,16),0,
                at(start,8),at(start,0),3,(int)(end >>> 32),1,(int)(start >>> 32),1,BROWSER.length(),0,0,0};
        int check = 0;
        for (int value : values) check ^= value;
        int[] body = Arrays.copyOf(values, values.length + BROWSER.length() + 1);
        for (int i = 0; i < BROWSER.length(); i++) body[values.length + i] = BROWSER.charAt(i);
        body[body.length - 1] = check;
        int[] encrypted = rc4(body);
        int[] combined = new int[12 + encrypted.length];
        for (int i = 0; i < 3; i++) {
            int low = random[i] & 255, high = random[i] >>> 8;
            combined[i*4] = low & 170 | 1;
            combined[i*4+1] = low & 85 | (i == 0 ? 2 : 0);
            combined[i*4+2] = high & 170 | (i == 1 ? 0 : 5);
            combined[i*4+3] = high & 85 | (i == 0 ? 40 : 0);
        }
        System.arraycopy(encrypted, 0, combined, 12, encrypted.length);
        return encode(combined);
    }

    private static int at(long value, int bits) { return (int)(value >>> bits) & 255; }

    private static int[] rc4(int[] plain) {
        int[] state = new int[256];
        for (int i = 0; i < state.length; i++) state[i] = i;
        int j = 0;
        for (int i = 0; i < state.length; i++) {
            j = (j + state[i] + 'y') & 255;
            int swap = state[i]; state[i] = state[j]; state[j] = swap;
        }
        int i = 0;
        j = 0;
        int[] result = new int[plain.length];
        for (int n = 0; n < plain.length; n++) {
            i = (i + 1) & 255;
            j = (j + state[i]) & 255;
            int swap = state[i]; state[i] = state[j]; state[j] = swap;
            result[n] = plain[n] ^ state[(state[i] + state[j]) & 255];
        }
        return result;
    }

    private static String encode(int[] values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i += 3) {
            int length = Math.min(3, values.length - i);
            int number = values[i] << 16;
            if (length > 1) number |= values[i+1] << 8;
            if (length > 2) number |= values[i+2];
            for (int shift = 18; shift >= 0; shift -= 6) {
                if (shift == 6 && length < 2 || shift == 0 && length < 3) break;
                result.append(ALPHABET.charAt((number >>> shift) & 63));
            }
        }
        while (result.length() % 4 != 0) result.append('=');
        return result.toString();
    }

    static byte[] sm3(byte[] input) {
        byte[] padded = Arrays.copyOf(input, (input.length + 9 + 63) / 64 * 64);
        padded[input.length] = (byte)128;
        long bits = (long)input.length * 8;
        for (int i = 0; i < 8; i++) padded[padded.length - 1 - i] = (byte)(bits >>> (8*i));
        int[] registers = {0x7380166f,0x4914b2b9,0x172442d7,0xda8a0600,
                0xa96f30bc,0x163138aa,0xe38dee4d,0xb0fb0e4e};
        int[] words = new int[68];
        for (int offset = 0; offset < padded.length; offset += 64) {
            for (int i = 0; i < 16; i++) {
                int p = offset + 4*i;
                words[i] = (padded[p]&255)<<24 | (padded[p+1]&255)<<16
                        | (padded[p+2]&255)<<8 | padded[p+3]&255;
            }
            for (int i = 16; i < 68; i++) {
                int value = words[i-16] ^ words[i-9] ^ Integer.rotateLeft(words[i-3],15);
                words[i] = value ^ Integer.rotateLeft(value,15) ^ Integer.rotateLeft(value,23)
                        ^ Integer.rotateLeft(words[i-13],7) ^ words[i-6];
            }
            int a=registers[0], b=registers[1], c=registers[2], d=registers[3];
            int e=registers[4], f=registers[5], g=registers[6], h=registers[7];
            for (int j = 0; j < 64; j++) {
                int rotatedA = Integer.rotateLeft(a,12);
                int ss1 = Integer.rotateLeft(rotatedA + e
                        + Integer.rotateLeft(j < 16 ? 0x79cc4519 : 0x7a879d8a,j),7);
                int ss2 = ss1 ^ rotatedA;
                int ff = j < 16 ? a^b^c : a&b | a&c | b&c;
                int gg = j < 16 ? e^f^g : e&f | ~e&g;
                int tt1 = ff + d + ss2 + (words[j] ^ words[j+4]);
                int tt2 = gg + h + ss1 + words[j];
                d=c; c=Integer.rotateLeft(b,9); b=a; a=tt1;
                h=g; g=Integer.rotateLeft(f,19); f=e;
                e=tt2 ^ Integer.rotateLeft(tt2,9) ^ Integer.rotateLeft(tt2,17);
            }
            int[] state = {a,b,c,d,e,f,g,h};
            for (int i = 0; i < 8; i++) registers[i] ^= state[i];
        }
        byte[] digest = new byte[32];
        for (int i = 0; i < registers.length; i++) {
            for (int j = 0; j < 4; j++) digest[i*4+j] = (byte)(registers[i] >>> (24-j*8));
        }
        return digest;
    }
}
