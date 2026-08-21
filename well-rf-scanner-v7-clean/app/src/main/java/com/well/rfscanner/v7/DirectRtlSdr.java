package com.well.rfscanner.v7;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import java.io.IOException;

/**
 * Direct RTL2832U + R820T/R820T2 driver for Android USB Host.
 * Register flow follows the Apache-2.0 rtlsdrjs implementation.
 */
public final class DirectRtlSdr {
    private static final int BLOCK_USB = 0x100;
    private static final int BLOCK_SYS = 0x200;
    private static final int BLOCK_I2C = 0x600;
    private static final int WRITE_FLAG = 0x10;

    private static final int REG_SYSCTL = 0x2000;
    private static final int REG_EPA_CTL = 0x2148;
    private static final int REG_EPA_MAXPKT = 0x2158;
    private static final int REG_DEMOD_CTL = 0x3000;
    private static final int REG_DEMOD_CTL_1 = 0x300b;

    private static final int XTAL_FREQ = 28_800_000;
    private static final int IF_FREQ = 3_570_000;
    private static final int TIMEOUT = 3500;
    private static final int CTRL_RETRIES = 3;

    private final UsbDevice device;
    private final UsbDeviceConnection conn;
    private UsbInterface iface;
    private UsbEndpoint bulkIn;
    private final R820T tuner;
    private final double ppm;

    public DirectRtlSdr(UsbDevice device, UsbDeviceConnection conn, double ppm) {
        this.device = device;
        this.conn = conn;
        this.ppm = ppm;
        this.tuner = new R820T((int)Math.floor(XTAL_FREQ * (1.0 + ppm / 1_000_000.0)));
    }

    public void open() throws IOException {
        if (device.getInterfaceCount() < 1) throw new IOException("RTL-SDR interface missing");
        iface = device.getInterface(0);

        // IMPORTANT: initialize the RTL2832U USB block before claiming interface 0.
        // This mirrors the known-working librtlsdr/rtlsdrjs startup order and avoids
        // Samsung/Android devices returning -1 on the first demodulator control write.
        writeReg(BLOCK_USB, REG_SYSCTL, 0x09, 1);
        writeReg(BLOCK_USB, REG_EPA_MAXPKT, 0x0200, 2);
        writeReg(BLOCK_USB, REG_EPA_CTL, 0x0210, 2);

        if (!conn.claimInterface(iface, true)) throw new IOException("Claim USB interface failed");
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint ep = iface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.getDirection() == UsbConstants.USB_DIR_IN) {
                bulkIn = ep;
                break;
            }
        }
        if (bulkIn == null) throw new IOException("RTL-SDR bulk IN endpoint missing");

        writeReg(BLOCK_SYS, REG_DEMOD_CTL_1, 0x22, 1);
        writeReg(BLOCK_SYS, REG_DEMOD_CTL, 0xe8, 1);
        demodWrite(1, 0x01, 0x14, 1);
        demodWrite(1, 0x01, 0x10, 1);
        demodWrite(1, 0x15, 0x00, 1);
        demodWrite(1, 0x16, 0x0000, 2);
        demodWrite(1, 0x16, 0x00, 1);
        demodWrite(1, 0x17, 0x00, 1);
        demodWrite(1, 0x18, 0x00, 1);
        demodWrite(1, 0x19, 0x00, 1);
        demodWrite(1, 0x1a, 0x00, 1);
        demodWrite(1, 0x1b, 0x00, 1);
        int[] fir = {0xca,0xdc,0xd7,0xd8,0xe0,0xf2,0x0e,0x35,0x06,0x50,0x9c,0x0d,0x71,0x11,0x14,0x71,0x74,0x19,0x41,0xa5};
        for (int i=0;i<fir.length;i++) demodWrite(1,0x1c+i,fir[i],1);
        demodWrite(0, 0x19, 0x05, 1);
        demodWrite(1, 0x93, 0xf0, 1);
        demodWrite(1, 0x94, 0x0f, 1);
        demodWrite(1, 0x11, 0x00, 1);
        demodWrite(1, 0x04, 0x00, 1);
        demodWrite(0, 0x61, 0x60, 1);
        demodWrite(0, 0x06, 0x80, 1);
        demodWrite(1, 0xb1, 0x1b, 1);
        demodWrite(0, 0x0d, 0x83, 1);

        i2cOpen();
        int id = i2cReadReg(0x34, 0x00);
        if (id != 0x69) {
            i2cClose();
            throw new IOException("R820T/R820T2 tuner not detected: 0x" + Integer.toHexString(id));
        }

        int xtal = (int)Math.floor(XTAL_FREQ * (1.0 + ppm / 1_000_000.0));
        int multiplier = -1 * (int)Math.floor((double)IF_FREQ * (1 << 22) / xtal);
        demodWrite(1, 0xb1, 0x1a, 1);
        demodWrite(0, 0x08, 0x4d, 1);
        demodWrite(1, 0x19, (multiplier >> 16) & 0x3f, 1);
        demodWrite(1, 0x1a, (multiplier >> 8) & 0xff, 1);
        demodWrite(1, 0x1b, multiplier & 0xff, 1);
        demodWrite(1, 0x15, 0x01, 1);
        tuner.init();
        tuner.setAutoGain();
        i2cClose();
    }

    public int setSampleRate(int rate) throws IOException {
        long ratio = ((long)XTAL_FREQ * (1L << 22)) / rate;
        ratio &= 0x0ffffffcL;
        int realRate = (int)(((long)XTAL_FREQ * (1L << 22)) / ratio);
        int ppmOffset = -1 * (int)Math.floor(ppm * (1 << 24) / 1_000_000.0);
        demodWrite(1, 0x9f, (int)((ratio >> 16) & 0xffff), 2);
        demodWrite(1, 0xa1, (int)(ratio & 0xffff), 2);
        demodWrite(1, 0x3e, (ppmOffset >> 8) & 0x3f, 1);
        demodWrite(1, 0x3f, ppmOffset & 0xff, 1);
        resetDemod();
        return realRate;
    }

    public long setCenterFrequency(long freqHz) throws IOException {
        i2cOpen();
        long actual = tuner.setFrequency(freqHz + IF_FREQ);
        i2cClose();
        return actual - IF_FREQ;
    }

    public void resetBuffer() throws IOException {
        writeReg(BLOCK_USB, REG_EPA_CTL, 0x0210, 2);
        writeReg(BLOCK_USB, REG_EPA_CTL, 0x0000, 2);
    }

    public int read(byte[] buffer, int timeoutMs) {
        if (bulkIn == null || conn == null) return -1;
        return conn.bulkTransfer(bulkIn, buffer, buffer.length, Math.max(300, timeoutMs));
    }

    public void close() {
        try { i2cOpen(); tuner.close(); i2cClose(); } catch (Throwable ignored) {}
        try { if (iface != null) conn.releaseInterface(iface); } catch (Throwable ignored) {}
    }

    private void resetDemod() throws IOException {
        demodWrite(1, 0x01, 0x14, 1);
        demodWrite(1, 0x01, 0x10, 1);
    }

    private int ctrlOut(int value, int index, byte[] data, String where) throws IOException {
        int r = -1;
        for (int n=0; n<CTRL_RETRIES; n++) {
            r = conn.controlTransfer(UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR,
                    0, value, index, data, data.length, TIMEOUT);
            if (r >= 0) return r;
            try { Thread.sleep(25L * (n + 1)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new IOException(where + " failed v=0x" + Integer.toHexString(value) + " i=0x" + Integer.toHexString(index) + " rc=" + r);
    }

    private int ctrlIn(int value, int index, byte[] data, int length, String where) throws IOException {
        int r = -1;
        for (int n=0; n<CTRL_RETRIES; n++) {
            r = conn.controlTransfer(UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR,
                    0, value, index, data, length, TIMEOUT);
            if (r >= 0) return r;
            try { Thread.sleep(25L * (n + 1)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new IOException(where + " failed v=0x" + Integer.toHexString(value) + " i=0x" + Integer.toHexString(index) + " rc=" + r);
    }

    private void writeReg(int block, int reg, int value, int len) throws IOException {
        byte[] b = numberToBytes(value, len, false);
        int r = ctrlOut(reg, block | WRITE_FLAG, b, "USB write reg");
        if (r != b.length) throw new IOException("USB short write reg " + r + "/" + b.length);
    }

    private int readReg(int block, int reg, int len) throws IOException {
        int requestLen = Math.max(8, len);
        byte[] b = new byte[requestLen];
        int r = ctrlIn(reg, block, b, requestLen, "USB read reg");
        if (r < len) throw new IOException("USB short read reg " + r + "/" + len);
        int v = 0;
        for (int i = 0; i < len; i++) v |= (b[i] & 0xff) << (8 * i);
        return v;
    }

    private void writeRegBuffer(int block, int reg, byte[] data) throws IOException {
        int r = ctrlOut(reg, block | WRITE_FLAG, data, "USB write buffer");
        if (r != data.length) throw new IOException("USB short write buffer " + r + "/" + data.length);
    }

    private byte[] readRegBuffer(int block, int reg, int len) throws IOException {
        byte[] b = new byte[Math.max(8, len)];
        int r = ctrlIn(reg, block, b, b.length, "USB read buffer");
        if (r < len) throw new IOException("USB short read buffer " + r + "/" + len);
        byte[] out = new byte[len];
        System.arraycopy(b, 0, out, 0, len);
        return out;
    }

    private int demodRead(int page, int addr) throws IOException {
        return readReg(page, (addr << 8) | 0x20, 1);
    }

    private void demodWrite(int page, int addr, int value, int len) throws IOException {
        writeRegBuffer(page, (addr << 8) | 0x20, numberToBytes(value, len, true));
        demodRead(0x0a, 0x01);
    }

    private void i2cOpen() throws IOException { demodWrite(1, 1, 0x18, 1); }
    private void i2cClose() throws IOException { demodWrite(1, 1, 0x10, 1); }

    private int i2cReadReg(int addr, int reg) throws IOException {
        writeRegBuffer(BLOCK_I2C, addr, new byte[]{(byte)reg});
        return readReg(BLOCK_I2C, addr, 1);
    }

    private byte[] i2cReadRegBuffer(int addr, int reg, int len) throws IOException {
        writeRegBuffer(BLOCK_I2C, addr, new byte[]{(byte)reg});
        return readRegBuffer(BLOCK_I2C, addr, len);
    }

    private void i2cWriteReg(int addr, int reg, int value) throws IOException {
        writeRegBuffer(BLOCK_I2C, addr, new byte[]{(byte)reg, (byte)value});
    }

    private static byte[] numberToBytes(int value, int len, boolean bigEndian) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) {
            int shift = bigEndian ? 8 * (len - 1 - i) : 8 * i;
            b[i] = (byte)((value >> shift) & 0xff);
        }
        return b;
    }

    private final class R820T {
        private final int xtalFreq;
        private final int[] regs = {0x83,0x32,0x75,0xc0,0x40,0xd6,0x6c,0xf5,0x63,0x75,0x68,0x6c,0x83,0x80,0x00,0x0f,0x00,0xc0,0x30,0x48,0xcc,0x60,0x00,0x54,0xae,0x4a,0xc0};
        private final int[][] mux = {
                {0,0x08,0x02,0xdf},{50,0x08,0x02,0xbe},{55,0x08,0x02,0x8b},{60,0x08,0x02,0x7b},
                {65,0x08,0x02,0x69},{70,0x08,0x02,0x58},{75,0x00,0x02,0x44},{90,0x00,0x02,0x34},
                {110,0x00,0x02,0x24},{140,0x00,0x02,0x14},{180,0x00,0x02,0x13},{250,0x00,0x02,0x11},
                {280,0x00,0x02,0x00},{310,0x00,0x41,0x00},{588,0x00,0x40,0x00}
        };
        private final int[] bitRev = {0x0,0x8,0x4,0xc,0x2,0xa,0x6,0xe,0x1,0x9,0x5,0xd,0x3,0xb,0x7,0xf};
        private int[] shadow;
        private boolean pllLock;

        R820T(int xtalFreq) { this.xtalFreq = xtalFreq; }

        void init() throws IOException {
            shadow = regs.clone();
            for (int i=0;i<regs.length;i++) i2cWriteReg(0x34, i+5, regs[i]);
            writeEach(new int[][]{{0x0c,0x00,0x0f},{0x13,49,0x3f},{0x1d,0x00,0x38}});
            int filterCap = calibrateFilter(true);
            writeEach(new int[][]{
                    {0x0a,0x10|filterCap,0x1f},{0x0b,0x6b,0xef},{0x07,0x00,0x80},{0x06,0x10,0x30},
                    {0x1e,0x40,0x60},{0x05,0x00,0x80},{0x1f,0x00,0x80},{0x0f,0x00,0x80},
                    {0x19,0x60,0x60},{0x1d,0xe5,0xc7},{0x1c,0x24,0xf8},{0x0d,0x53,0xff},
                    {0x0e,0x75,0xff},{0x05,0x00,0x60},{0x06,0x00,0x08},{0x11,0x38,0x08},
                    {0x17,0x30,0x30},{0x0a,0x40,0x60},{0x1d,0x00,0x38},{0x1c,0x00,0x04},
                    {0x06,0x00,0x40},{0x1a,0x30,0x30},{0x1d,0x18,0x38},{0x1c,0x24,0x04},
                    {0x1e,0x0d,0x1f},{0x1a,0x20,0x30}
            });
        }

        void setAutoGain() throws IOException {
            writeEach(new int[][]{{0x05,0x00,0x10},{0x07,0x10,0x10},{0x0c,0x0b,0x9f}});
        }

        long setFrequency(long freq) throws IOException {
            setMux(freq);
            return setPll(freq);
        }

        void close() throws IOException {
            writeEach(new int[][]{{0x06,0xb1,0xff},{0x05,0xb3,0xff},{0x07,0x3a,0xff},{0x08,0x40,0xff},
                    {0x09,0xc0,0xff},{0x0a,0x36,0xff},{0x0c,0x35,0xff},{0x0f,0x68,0xff},
                    {0x11,0x03,0xff},{0x17,0xf4,0xff},{0x19,0x0c,0xff}});
        }

        private int calibrateFilter(boolean first) throws IOException {
            writeEach(new int[][]{{0x0b,0x6b,0x60},{0x0f,0x04,0x04},{0x10,0x00,0x03}});
            setPll(56_000_000L);
            if (!pllLock) throw new IOException("R820T filter PLL not locked");
            writeEach(new int[][]{{0x0b,0x10,0x10},{0x0b,0x00,0x10},{0x0f,0x00,0x04}});
            byte[] d = readTunerRegs(0,5);
            int filterCap = d[4] & 0x0f;
            if (filterCap == 0x0f) filterCap = 0;
            if (filterCap != 0 && first) return calibrateFilter(false);
            return filterCap;
        }

        private void setMux(long freq) throws IOException {
            double mhz = freq / 1_000_000.0;
            int idx=0;
            for (int i=0;i<mux.length-1;i++) { idx=i; if (mhz < mux[i+1][0]) break; }
            if (mhz >= mux[mux.length-1][0]) idx=mux.length-1;
            int[] c=mux[idx];
            writeEach(new int[][]{{0x17,c[1],0x08},{0x1a,c[2],0xc3},{0x1b,c[3],0xff},{0x10,0x00,0x0b},{0x08,0x00,0x3f},{0x09,0x00,0x3f}});
        }

        private long setPll(long freq) throws IOException {
            writeEach(new int[][]{{0x10,0x00,0x10},{0x1a,0x00,0x0c},{0x12,0x80,0xe0}});
            int divNum = Math.min(6, (int)Math.floor(Math.log(1_770_000_000.0 / freq) / Math.log(2.0)));
            if (divNum < 0) divNum = 0;
            int mixDiv = 1 << (divNum + 1);
            byte[] d = readTunerRegs(0,5);
            int fine = (d[4] & 0x30) >> 4;
            if (fine > 2) divNum--; else if (fine < 2) divNum++;
            if (divNum < 0) divNum=0; if (divNum > 6) divNum=6;
            writeMask(0x10, divNum << 5, 0xe0);
            long vcoFreq = freq * mixDiv;
            long nint = vcoFreq / (2L * xtalFreq);
            long vcoFra = vcoFreq % (2L * xtalFreq);
            if (nint > 63 || nint < 13) { pllLock=false; throw new IOException("R820T PLL range"); }
            int ni=(int)((nint-13)/4); int si=(int)((nint-13)%4);
            writeEach(new int[][]{{0x14,ni+(si<<6),0xff},{0x12,vcoFra==0?0x08:0x00,0x08}});
            int sdm=(int)Math.min(65535L, (32768L*vcoFra)/xtalFreq);
            writeEach(new int[][]{{0x16,(sdm>>8)&0xff,0xff},{0x15,sdm&0xff,0xff}});
            getPllLock(true);
            writeMask(0x1a,0x08,0x08);
            return (long)(2.0*xtalFreq*(nint+sdm/65536.0)/mixDiv);
        }

        private void getPllLock(boolean first) throws IOException {
            byte[] d=readTunerRegs(0,3);
            if ((d[2]&0x40)!=0) { pllLock=true; return; }
            if (first) { writeMask(0x12,0x60,0xe0); getPllLock(false); }
            else pllLock=false;
        }

        private byte[] readTunerRegs(int addr,int len) throws IOException {
            byte[] d=i2cReadRegBuffer(0x34,addr,len);
            for (int i=0;i<d.length;i++) { int b=d[i]&0xff; d[i]=(byte)((bitRev[b&0xf]<<4)|bitRev[(b>>4)&0xf]); }
            return d;
        }

        private void writeMask(int addr,int value,int mask) throws IOException {
            int rc=shadow[addr-5];
            int val=(rc & ~mask) | (value & mask);
            shadow[addr-5]=val;
            i2cWriteReg(0x34,addr,val);
        }

        private void writeEach(int[][] a) throws IOException {
            for (int[] x:a) writeMask(x[0],x[1],x[2]);
        }
    }
}
