package org.jingbling.ContextEngine;

interface IContextService
{
    String getContext(in List<String> featuresToUse, String classifierToUse, String contextGroup);
    void gatherTrainingData(in List<String> featuresToUse, String contextGroup, String filename);
}
