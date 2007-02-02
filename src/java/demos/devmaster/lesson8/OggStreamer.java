/**
 * Copyright (c) 2003 Sun Microsystems, Inc. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * -Redistribution of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright notice, 
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of contributors may 
 * be used to endorse or promote products derived from this software without 
 * specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind.
 * ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN") AND ITS
 * LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A
 * RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 * IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT
 * OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR
 * PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed or intended for use in the
 * design, construction, operation or maintenance of any nuclear facility.
 *
 */

package demos.devmaster.lesson8;

import java.io.File;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.java.games.joal.AL;
import net.java.games.joal.ALException;
import net.java.games.joal.ALFactory;
import net.java.games.joal.util.ALut;
/**
 *
 * This is a translation of the OggVorbis streamer OpenAL tutorial 
 * at http://www.devmaster.net/articles/openal-tutorials/lesson8.php
 *
 * It uses the Java Ogg library from http://www.j-ogg.de to do the Ogg
 * file decoding...
 *
 * @author Krishna K Gadepalli
 */
public class OggStreamer {
    
    OggDecoder oggDecoder;
    
    static AL al = null;
    static int BUFFER_SIZE = 4096*4;
    
    static {
        // Initialize OpenAL and clear the error bit.
        try {
            ALut.alutInit();
            al = ALFactory.getAL();
            al.alGetError();
        } catch (ALException e) {
            System.err.println("Error initializing OpenAL");
            e.printStackTrace();
        }
    }
    
    // Buffers hold sound data.
    private int[] buffers = new int[2];
    
    // Sources are points emitting sound.
    private int[] source = new int[1];
    
    // Position, Velocity, Direction of the source sound.
    private float[] sourcePos = { 0.0f, 0.0f, 0.0f };
    private float[] sourceVel = { 0.0f, 0.0f, 0.0f };
    private float[] sourceDir = { 0.0f, 0.0f, 0.0f };
    
    private int format;	// OpenAL data format
    private int rate;	// sample rate
    
    /** Creates a new instance of OggStreamer */
    public OggStreamer(URL url) {
        this.oggDecoder = new OggDecoder(url);
    }
    
    public boolean open() {
        if (!oggDecoder.initialize()) {
            System.err.println("Error initializing ogg stream...");
            return false;
        }
        
	// TODO: I am not if this is the right way to fix the endian
	// problems I am having... but this seems to fix it on Linux
	oggDecoder.setSwap(true);

        switch (oggDecoder.numChannels()) {
            case 1:	format = AL.AL_FORMAT_MONO16;	break;
            case 2:	format = AL.AL_FORMAT_STEREO16;	break;
            default:
                System.err.println("Incorrect number of channels..");
                return false;
        }
        
	rate = oggDecoder.sampleRate();

        al.alGenBuffers(2, buffers, 0); check();
        al.alGenSources(1, source, 0); check();

	System.err.println("format  = 0x" + Integer.toString(format, 16));
	// System.err.println("buffers = " + Arrays.toString(buffers));
	// System.err.println("source  = " + Arrays.toString(source ));

	al.alSourcefv(source[0], AL.AL_POSITION , sourcePos, 0);
	al.alSourcefv(source[0], AL.AL_VELOCITY , sourceVel, 0);
	al.alSourcefv(source[0], AL.AL_DIRECTION, sourceDir, 0);
        
        al.alSourcef(source[0], AL.AL_ROLLOFF_FACTOR,  0.0f    );
        al.alSourcei(source[0], AL.AL_SOURCE_RELATIVE, AL.AL_TRUE);
        
        return true;
    }
    
    public void release() {
	al.alSourceStop(source[0]);
	empty();

	al.alDeleteSources(1, source, 0); check();
	al.alDeleteBuffers(2, buffers, 0); check();

	// ov_clear(&oggStream);
    }

    public boolean playback() {
	if (playing())
	    return true;
        
	if (!stream(buffers[0]))
	    return false;
        
	if(!stream(buffers[1]))
	    return false;
    
	al.alSourceQueueBuffers(source[0], 2, buffers, 0);
	al.alSourcePlay(source[0]);
    
        return true;
    }
    
    public boolean playing() {
	int[] state = new int[1];
    
	al.alGetSourcei(source[0], AL.AL_SOURCE_STATE, state, 0);
    
	return (state[0] == AL.AL_PLAYING);
    }
    
    public boolean update() {
	int[] processed = new int[1];
	boolean active = true;

	al.alGetSourcei(source[0], AL.AL_BUFFERS_PROCESSED, processed, 0);

	while (processed[0] > 0)
	{
	    int[] buffer = new int[1];
	    
	    al.alSourceUnqueueBuffers(source[0], 1, buffer, 0); check();

	    active = stream(buffer[0]);

	    al.alSourceQueueBuffers(source[0], 1, buffer, 0); check();

	    processed[0]--;
	}

	return active;
    }
    
    public boolean stream(int buffer) {
	byte[] pcm = new byte[BUFFER_SIZE];
	int    size = 0;

	try {
	    if ((size = oggDecoder.read(pcm)) <= 0)
		return false;
	} catch (Exception e) {
	    e.printStackTrace();
	    return false;
	}

	ByteBuffer data = ByteBuffer.wrap(pcm, 0, size);
	al.alBufferData(buffer, format, data, size, rate);
	check();
	
	return true;
    }

    public void empty() {
    }

    private void check() {
        if (al.alGetError() != AL.AL_NO_ERROR)
            throw new ALException("OpenAL error raised...");
    }
    
    public boolean play() {
        if (!open())
            return false;
        
        oggDecoder.dump();
        
        if (!playback())
            return false;
        
        while (update()) {
            if (playing()) continue;
            
            if (!playback())
                return false;
        }
        
        return true;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (al == null)
            return;
        
	URL url;

        try {
	    if (args.length == 0) {
		url = OggStreamer.class.getClassLoader().getResource("demos/data/crickets.ogg");
		(new OggStreamer(url)).play();
	    }

            for (int i = 0; i < args.length; i++) {
                System.err.println("Playing Ogg stream : " + args[i]);
                
                url = ((new File(args[i])).exists()) ?
                    new URL("file:" + args[i]) : new URL(args[i]);
                
                if ((new OggStreamer(url)).play()) continue;
                
                System.err.println("ERROR!!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
