#include "PluginEditor.h"

WellClearWarmEQAudioProcessorEditor::WellClearWarmEQAudioProcessorEditor (WellClearWarmEQAudioProcessor& p)
    : AudioProcessorEditor(&p), processor(p)
{
    setSize(760, 330);
    title.setText("Well ClearWarm EQ", juce::dontSendNotification);
    title.setFont(juce::FontOptions(28.0f, juce::Font::bold));
    title.setJustificationType(juce::Justification::centredLeft);
    addAndMakeVisible(title);

    subtitle.setText("CLEAR  •  DEFINED  •  WARM", juce::dontSendNotification);
    subtitle.setFont(juce::FontOptions(13.0f, juce::Font::bold));
    subtitle.setJustificationType(juce::Justification::centredLeft);
    addAndMakeVisible(subtitle);

    const std::array<const char*, 6> ids { "clean", "warmth", "clarity", "harsh", "mix", "output" };
    const std::array<const char*, 6> names { "CLEAN", "WARMTH", "CLARITY", "HARSH", "CHARACTER", "OUTPUT" };
    for (size_t i = 0; i < knobs.size(); ++i)
    {
        auto& k = knobs[i];
        k.slider.setSliderStyle(juce::Slider::RotaryHorizontalVerticalDrag);
        k.slider.setTextBoxStyle(juce::Slider::TextBoxBelow, false, 70, 20);
        if (i < 5) k.slider.setTextValueSuffix(" %"); else k.slider.setTextValueSuffix(" dB");
        k.label.setText(names[i], juce::dontSendNotification);
        k.label.setJustificationType(juce::Justification::centred);
        addAndMakeVisible(k.slider); addAndMakeVisible(k.label);
        k.attachment = std::make_unique<juce::AudioProcessorValueTreeState::SliderAttachment>(processor.apvts, ids[i], k.slider);
    }
    addAndMakeVisible(bypass);
    bypassAttachment = std::make_unique<juce::AudioProcessorValueTreeState::ButtonAttachment>(processor.apvts, "bypass", bypass);
}

void WellClearWarmEQAudioProcessorEditor::paint (juce::Graphics& g)
{
    g.fillAll(juce::Colour::fromRGB(15, 17, 20));
    auto r = getLocalBounds().toFloat().reduced(14.0f);
    g.setColour(juce::Colour::fromRGB(41, 45, 52));
    g.drawRoundedRectangle(r, 14.0f, 1.5f);
    g.setColour(juce::Colours::white.withAlpha(0.08f));
    g.fillRoundedRectangle(juce::Rectangle<float>(18, 86, getWidth()-36.0f, 210), 10.0f);
}

void WellClearWarmEQAudioProcessorEditor::resized()
{
    title.setBounds(28, 20, 400, 38);
    subtitle.setBounds(30, 55, 320, 22);
    bypass.setBounds(getWidth()-120, 30, 90, 28);

    const int y = 112, knobW = 112, gap = 7;
    int x = 30;
    for (auto& k : knobs)
    {
        k.slider.setBounds(x, y, knobW, 130);
        k.label.setBounds(x, y + 136, knobW, 24);
        x += knobW + gap;
    }
}
