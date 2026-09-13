#include "PluginProcessor.h"
#include "PluginEditor.h"
#include <cmath>

WellClearWarmEQAudioProcessor::WellClearWarmEQAudioProcessor()
    : AudioProcessor(BusesProperties().withInput("Input", juce::AudioChannelSet::stereo(), true)
                                     .withOutput("Output", juce::AudioChannelSet::stereo(), true)),
      apvts(*this, nullptr, "PARAMETERS", createParameterLayout())
{
}

juce::AudioProcessorValueTreeState::ParameterLayout WellClearWarmEQAudioProcessor::createParameterLayout()
{
    std::vector<std::unique_ptr<juce::RangedAudioParameter>> p;
    auto pct = juce::NormalisableRange<float>(0.0f, 100.0f, 0.1f);
    p.push_back(std::make_unique<juce::AudioParameterFloat>(juce::ParameterID{"clean",1}, "Clean", pct, 48.0f));
    p.push_back(std::make_unique<juce::AudioParameterFloat>(juce::ParameterID{"warmth",1}, "Warmth", pct, 42.0f));
    p.push_back(std::make_unique<juce::AudioParameterFloat>(juce::ParameterID{"clarity",1}, "Clarity", pct, 46.0f));
    p.push_back(std::make_unique<juce::AudioParameterFloat>(juce::ParameterID{"harsh",1}, "Harsh Control", pct, 35.0f));
    p.push_back(std::make_unique<juce::AudioParameterFloat>(juce::ParameterID{"mix",1}, "Character Mix", pct, 100.0f));
    p.push_back(std::make_unique<juce::AudioParameterFloat>(juce::ParameterID{"output",1}, "Output", -12.0f, 6.0f, 0.0f));
    p.push_back(std::make_unique<juce::AudioParameterBool>(juce::ParameterID{"bypass",1}, "Bypass", false));
    return { p.begin(), p.end() };
}

bool WellClearWarmEQAudioProcessor::isBusesLayoutSupported (const BusesLayout& layouts) const
{
    const auto in = layouts.getMainInputChannelSet();
    const auto out = layouts.getMainOutputChannelSet();
    return in == out && (out == juce::AudioChannelSet::mono() || out == juce::AudioChannelSet::stereo());
}

void WellClearWarmEQAudioProcessor::prepareToPlay (double sampleRate, int samplesPerBlock)
{
    sr = sampleRate;
    juce::dsp::ProcessSpec spec { sampleRate, (juce::uint32) samplesPerBlock, 1 };
    for (auto& c : channels)
    {
        c.hp.prepare(spec); c.cleanBell.prepare(spec); c.warmShelf.prepare(spec);
        c.clarityBell.prepare(spec); c.airShelf.prepare(spec); c.harshBell.prepare(spec);
        c.hp.reset(); c.cleanBell.reset(); c.warmShelf.reset(); c.clarityBell.reset(); c.airShelf.reset(); c.harshBell.reset();
    }

    for (auto* s : { &cleanSmoothed, &warmthSmoothed, &claritySmoothed, &harshSmoothed, &outputSmoothed, &mixSmoothed })
        s->reset(sampleRate, 0.03);

    cleanSmoothed.setCurrentAndTargetValue(apvts.getRawParameterValue("clean")->load());
    warmthSmoothed.setCurrentAndTargetValue(apvts.getRawParameterValue("warmth")->load());
    claritySmoothed.setCurrentAndTargetValue(apvts.getRawParameterValue("clarity")->load());
    harshSmoothed.setCurrentAndTargetValue(apvts.getRawParameterValue("harsh")->load());
    outputSmoothed.setCurrentAndTargetValue(apvts.getRawParameterValue("output")->load());
    mixSmoothed.setCurrentAndTargetValue(apvts.getRawParameterValue("mix")->load());
    updateFilters(cleanSmoothed.getCurrentValue(), warmthSmoothed.getCurrentValue(), claritySmoothed.getCurrentValue(), harshSmoothed.getCurrentValue());
}

void WellClearWarmEQAudioProcessor::updateFilters(float clean, float warmth, float clarity, float harsh)
{
    const float cleanDb = juce::jmap(clean, 0.0f, 100.0f, 0.0f, -4.5f);
    const float warmDb  = juce::jmap(warmth, 0.0f, 100.0f, 0.0f, 2.8f);
    const float presDb  = juce::jmap(clarity, 0.0f, 100.0f, 0.0f, 3.0f);
    const float airDb   = juce::jmap(clarity, 0.0f, 100.0f, 0.0f, 2.0f);
    const float harshDb = juce::jmap(harsh, 0.0f, 100.0f, 0.0f, -4.0f);

    auto hpC    = Coeff::makeHighPass(sr, 28.0f, 0.7071f);
    auto cleanC = Coeff::makePeakFilter(sr, 285.0f, 0.80f, juce::Decibels::decibelsToGain(cleanDb));
    auto warmC  = Coeff::makeLowShelf(sr, 150.0f, 0.70f, juce::Decibels::decibelsToGain(warmDb));
    auto presC  = Coeff::makePeakFilter(sr, 3200.0f, 0.75f, juce::Decibels::decibelsToGain(presDb));
    auto airC   = Coeff::makeHighShelf(sr, 9000.0f, 0.70f, juce::Decibels::decibelsToGain(airDb));
    auto harshC = Coeff::makePeakFilter(sr, 4700.0f, 1.35f, juce::Decibels::decibelsToGain(harshDb));

    for (auto& c : channels)
    {
        c.hp.coefficients = hpC;
        c.cleanBell.coefficients = cleanC;
        c.warmShelf.coefficients = warmC;
        c.clarityBell.coefficients = presC;
        c.airShelf.coefficients = airC;
        c.harshBell.coefficients = harshC;
    }
}

float WellClearWarmEQAudioProcessor::softSat(float x, float drive)
{
    const float d = 1.0f + drive * 2.2f;
    const float shaped = std::tanh(x * d + 0.035f * drive * x * x);
    const float norm = std::max(0.001f, std::tanh(d));
    return shaped / norm;
}

void WellClearWarmEQAudioProcessor::processBlock (juce::AudioBuffer<float>& buffer, juce::MidiBuffer&)
{
    juce::ScopedNoDenormals noDenormals;
    const int numCh = juce::jmin(buffer.getNumChannels(), 2);
    const int n = buffer.getNumSamples();

    if (apvts.getRawParameterValue("bypass")->load() > 0.5f)
        return;

    cleanSmoothed.setTargetValue(apvts.getRawParameterValue("clean")->load());
    warmthSmoothed.setTargetValue(apvts.getRawParameterValue("warmth")->load());
    claritySmoothed.setTargetValue(apvts.getRawParameterValue("clarity")->load());
    harshSmoothed.setTargetValue(apvts.getRawParameterValue("harsh")->load());
    outputSmoothed.setTargetValue(apvts.getRawParameterValue("output")->load());
    mixSmoothed.setTargetValue(apvts.getRawParameterValue("mix")->load());

    const float clean = cleanSmoothed.skip(n);
    const float warmth = warmthSmoothed.skip(n);
    const float clarity = claritySmoothed.skip(n);
    const float harsh = harshSmoothed.skip(n);
    updateFilters(clean, warmth, clarity, harsh);

    const float drive = juce::jmap(warmth, 0.0f, 100.0f, 0.0f, 0.42f);
    const float wet = juce::jlimit(0.0f, 1.0f, mixSmoothed.skip(n) * 0.01f);
    const float outGain = juce::Decibels::decibelsToGain(outputSmoothed.skip(n));

    for (int ch = 0; ch < numCh; ++ch)
    {
        auto* data = buffer.getWritePointer(ch);
        auto& d = channels[(size_t) ch];
        for (int i = 0; i < n; ++i)
        {
            const float dry = data[i];
            float x = dry;
            x = d.hp.processSample(x);
            x = d.cleanBell.processSample(x);
            x = d.warmShelf.processSample(x);
            x = softSat(x, drive);
            x = d.clarityBell.processSample(x);
            x = d.harshBell.processSample(x);
            x = d.airShelf.processSample(x);
            data[i] = (dry + (x - dry) * wet) * outGain;
        }
    }
}

void WellClearWarmEQAudioProcessor::getStateInformation (juce::MemoryBlock& destData)
{
    auto state = apvts.copyState();
    std::unique_ptr<juce::XmlElement> xml(state.createXml());
    copyXmlToBinary(*xml, destData);
}

void WellClearWarmEQAudioProcessor::setStateInformation (const void* data, int sizeInBytes)
{
    std::unique_ptr<juce::XmlElement> xml(getXmlFromBinary(data, sizeInBytes));
    if (xml != nullptr && xml->hasTagName(apvts.state.getType()))
        apvts.replaceState(juce::ValueTree::fromXml(*xml));
}

juce::AudioProcessorEditor* WellClearWarmEQAudioProcessor::createEditor()
{
    return new WellClearWarmEQAudioProcessorEditor(*this);
}

juce::AudioProcessor* JUCE_CALLTYPE createPluginFilter()
{
    return new WellClearWarmEQAudioProcessor();
}
