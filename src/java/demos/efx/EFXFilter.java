package demos.efx;

import net.java.games.joal.AL;
import net.java.games.joal.ALC;
import net.java.games.joal.ALCcontext;
import net.java.games.joal.ALCdevice;
import net.java.games.joal.ALFactory;
import net.java.games.joal.util.WAVData;
import net.java.games.joal.util.WAVLoader;

/**
 * OpenAL 1.1 EFX test class, a translation of the C++ EFXFilter demo from the
 * OpenAL 1.1 SDK.
 * 
 * @author Emanuel Rabina
 */
public class EFXFilter {

    private static ALCdevice device;
    private static ALCcontext context;
    private static ALC alc;
    private static AL al;

    private static String wavefile = "demos/data/Footsteps.wav";

    /**
     * Run something similar to the EFXFilter sample from the OpenAL 1.1
     * (w/ EFX) SDK.
     * 
     * @param args
     */
    public static void main(String[] args) {

        try {
            initOpenAL();

            // Requires EFX support
            if (alc.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {

                // Load the sound sample to a buffer
                WAVData wavedata = WAVLoader.loadFromStream(EFXFilter.class.getClassLoader().getResourceAsStream(wavefile));
                int[] buffers = new int[1];
                al.alGenBuffers(1, buffers, 0);
                al.alBufferData(buffers[0], wavedata.format, wavedata.data, wavedata.size, wavedata.freq);
                int buffer = buffers[0];

                // Attach buffer to a source
                int[] sources = new int[1];
                al.alGenSources(1, sources, 0);
                al.alSourcei(sources[0], AL.AL_BUFFER, buffer);
                int source = sources[0];

                // Play the sound through a variety of EFX filters & effects
                playDry(source);
                playDirectFilter(source);
                playAuxiliaryNoFilter(source);
                playAuxiliaryFilter(source);

                // Cleanup buffer & source
                al.alSourcei(source, AL.AL_BUFFER, 0);
                al.alDeleteSources(1, new int[]{ source }, 0);
                al.alDeleteBuffers(1, new int[]{ buffer }, 0);
            }
            else {
                System.out.println("EFX not supported.");
            }

            shutdownOpenAL();
            System.exit(0);
        }
        catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Set up an OpenAL context and make it current on this thread.
     */
    private static void initOpenAL() {

        alc = ALFactory.getALC();
        device = alc.alcOpenDevice(null);
        context = alc.alcCreateContext(device, null);
        alc.alcMakeContextCurrent(context);

        al = ALFactory.getAL();
    }

    /**
     * Release & destroy the OpenAL context and close the device.
     */
    private static void shutdownOpenAL() {

        alc.alcMakeContextCurrent(null);
        alc.alcDestroyContext(context);
        alc.alcCloseDevice(device);

        alc = null;
        al = null;
    }

    /**
     * Plays the source without any filters.
     * 
     * @param source Source ID.
     */
    private static void playDry(int source) {

        System.out.println("Source played dry");
        play(source);
    }

    /**
     * Plays the source with a low-pass filter.
     * 
     * @param source Source ID.
     */
    private static void playDirectFilter(int source) {

        System.out.println("Source played through a direct lowpass filter");

        // Attach a lowpass filter to the source
        int filter = createFilter(AL.AL_FILTER_LOWPASS, 1f, 0.5f);
        al.alSourcei(source, AL.AL_DIRECT_FILTER, filter);

        play(source);

        // Cleanup
        al.alSourcei(source, AL.AL_DIRECT_FILTER, AL.AL_FILTER_NULL);
        deleteFilter(filter);
    }

    /**
     * Plays the source through an auxiliary reverb with no filter.
     * 
     * @param source Source ID.
     */
    private static void playAuxiliaryNoFilter(int source) {

        System.out.println("Source played through an auxiliary reverb without filtering");

        // Create auxiliary effect slots & effects
        int[] effectslots = new int[1];
        al.alGenAuxiliaryEffectSlots(1, effectslots, 0);
        int[] effects = new int[1];
        al.alGenEffects(1, effects, 0);

        // Configure the effect to be a reverb, load it to the effect slot
        al.alEffecti(effects[0], AL.AL_EFFECT_TYPE, AL.AL_EFFECT_REVERB);
        al.alAuxiliaryEffectSloti(effectslots[0], AL.AL_EFFECTSLOT_EFFECT, effects[0]);

        // Enable Send 0 from the Source to the Auxiliary Effect Slot without filtering
        al.alSource3i(source, AL.AL_AUXILIARY_SEND_FILTER, effectslots[0], 0, AL.AL_FILTER_NULL);

        play(source);

        // Cleanup
        al.alSource3i(source, AL.AL_AUXILIARY_SEND_FILTER, AL.AL_EFFECTSLOT_NULL, 0, AL.AL_FILTER_NULL);
        al.alDeleteAuxiliaryEffectSlots(1, effectslots, 0);
        al.alDeleteEffects(1, effects, 0);
    }

    /**
     * Plays the source through an auxiliary reverb with a low-pass filter.
     * 
     * @param source SourceID.
     */
    private static void playAuxiliaryFilter(int source) {

        System.out.println("Source played through an auxiliary reverb with lowpass filter");

        // Create a lowpass filter, attach it to a reverb effect
        int filter = createFilter(AL.AL_FILTER_LOWPASS, 1f, 0.5f);

        int[] effectslots = new int[1];
        al.alGenAuxiliaryEffectSlots(1, effectslots, 0);
        int[] effects = new int[1];
        al.alGenEffects(1, effects, 0);
        al.alEffecti(effects[0], AL.AL_EFFECT_TYPE, AL.AL_EFFECT_REVERB);
        al.alAuxiliaryEffectSloti(effectslots[0], AL.AL_EFFECTSLOT_EFFECT, effects[0]);

        // Enable Send 0 from the Source to the Auxiliary Effect Slot with filtering
        al.alSource3i(source, AL.AL_AUXILIARY_SEND_FILTER, effectslots[0], 0, filter);

        play(source);

        // Cleanup
        al.alSource3i(source, AL.AL_AUXILIARY_SEND_FILTER, AL.AL_EFFECTSLOT_NULL, 0, AL.AL_FILTER_NULL);
        al.alDeleteAuxiliaryEffectSlots(1, effectslots, 0);
        al.alDeleteEffects(1, effects, 0);
    }

    /**
     * Plays the source, returning once the source has been completed.
     * 
     * @param source Source ID.
     */
    private static void play(int source) {

        al.alSourcePlay(source);

        while (true) {
            int[] state = new int[1];
            al.alGetSourcei(source, AL.AL_SOURCE_STATE, state, 0);

            if (state[0] == AL.AL_PLAYING) {
                try {
                    Thread.sleep(50);
                }
                catch (InterruptedException iex) {
                    throw new RuntimeException(iex.getMessage(), iex);
                }
            }
            else {
                break;
            }
        }
    }

    /**
     * Creates a new OpenAL EFX filter per the given specs.
     * 
     * @param filtertype Type of filter to create.  One of {@link AL#AL_FILTER_LOWPASS},
     * 					 or {@link AL#AL_FILTER_HIGHPASS}.
     * @param gain		 Filter gain.
     * @param gainlimit	 Filter frequency (upper/lower) limit.
     * @return Filter ID.
     */
    private static int createFilter(int filtertype, float gain, float gainlimit) {

        int filters[] = new int[1];
        al.alGenFilters(1, filters, 0);

        al.alFilteri(filters[0], AL.AL_FILTER_TYPE, filtertype);
        al.alFilterf(filters[0], filtertype, gain);
        al.alFilterf(filters[0], filtertype, gainlimit);

        return filters[0];
    }

    /**
     * Deletes the given OpenAL EFX filter.
     * 
     * @param filter Filter ID.
     */
    private static void deleteFilter(int filter) {

        al.alDeleteFilters(1, new int[]{ filter }, 0);
    }
}
