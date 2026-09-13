#pragma once
#include <JuceHeader.h>

class WellClearWarmEQAudioProcessor : public juce::AudioProcessor
{
public:
    WellClearWarmEQAudioProcessor();
    ~WellClearWarmEQAudioProcessor() override = default;

    void prepareToPlay (double sampleRate, int samplesPerBlock) override;
    void releaseResources() override {}
    bool isBusesLayoutSupported (const BusesLayout& layouts) const override;
    void processBlock (juce::AudioBuffer<float>&, juce::MidiBuffer&) override;

    juce::AudioProcessorEditor* createEditor() override;
    bool hasEditor() const override { return true; }

    const juce::String getName() const override { return JucePlugin_Name; }
    bool acceptsMidi() const override { return false; }
    bool producesMidi() const override { return false; }
    bool isMidiEffect() const override { return false; }
    double getTailLengthSeconds() const override { return 0.0; }

    int getNumPrograms() override { return 1; }
    int getCurrentProgram() override { return 0; }
    void setCurrentProgram (int) override {}
    const juce::String getProgramName (int) override { return {}; }
    void changeProgramName (int, const juce::String&) override {}

    void getStateInformation (juce::MemoryBlock& destData) override;
    void setStateInformation (const void* data, int sizeInBytes) override;

    juce::AudioProcessorValueTreeState apvts;

private:
    using Filter = juce::dsp::IIR::Filter<float>;
    using Coeff = juce::dsp::IIR::Coefficients<float>;

    struct ChannelDSP
    {
        Filter hp;
        Filter cleanBell;
        Filter warmShelf;
        Filter clarityBell;
        Filter airShelf;
        Filter harshBell;
    };

    std::array<ChannelDSP, 2> channels;
    double sr = 44100.0;

    juce::SmoothedValue<float> cleanSmoothed, warmthSmoothed, claritySmoothed,
                               harshSmoothed, outputSmoothed, mixSmoothed;

    static juce::AudioProcessorValueTreeState::ParameterLayout createParameterLayout();
    void updateFilters(float clean, float warmth, float clarity, float harsh);
    static float softSat(float x, float drive);

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (WellClearWarmEQAudioProcessor)
};
