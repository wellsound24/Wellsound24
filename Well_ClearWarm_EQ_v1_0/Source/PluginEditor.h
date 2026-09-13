#pragma once
#include <JuceHeader.h>
#include "PluginProcessor.h"

class WellClearWarmEQAudioProcessorEditor : public juce::AudioProcessorEditor
{
public:
    explicit WellClearWarmEQAudioProcessorEditor (WellClearWarmEQAudioProcessor&);
    ~WellClearWarmEQAudioProcessorEditor() override = default;
    void paint (juce::Graphics&) override;
    void resized() override;

private:
    WellClearWarmEQAudioProcessor& processor;
    struct Knob
    {
        juce::Slider slider;
        juce::Label label;
        std::unique_ptr<juce::AudioProcessorValueTreeState::SliderAttachment> attachment;
    };
    std::array<Knob, 6> knobs;
    juce::ToggleButton bypass { "BYPASS" };
    std::unique_ptr<juce::AudioProcessorValueTreeState::ButtonAttachment> bypassAttachment;
    juce::Label title, subtitle;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (WellClearWarmEQAudioProcessorEditor)
};
