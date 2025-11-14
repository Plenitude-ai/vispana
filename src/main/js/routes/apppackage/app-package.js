import SyntaxHighlighter from 'react-syntax-highlighter';
import {androidstudio} from 'react-syntax-highlighter/dist/esm/styles/hljs';
import React from 'react'
import {useOutletContext} from "react-router-dom";
import TabView from "../../components/tabs/tab-view";
import AppPackageExplorer from "../../components/file-explorer/app-package-explorer";
import SchemaDefinition from "../../components/schema-definition/schema-definition";

function AppPackage() {
    const vespaState = useOutletContext();

    const schemas = vespaState
        .content
        .clusters
        .flatMap(cluster => {
            return cluster
                .contentData
                .map(data => {
                    return {
                        "tabName": `${data.schema.schemaName}.sd`,
                        "contentType": "schema-definition",
                        "schemaContent": data.schema.schemaContent,
                        "rankProfiles": data.schema.schemaRankProfiles
                    };
                })
        })
        .sort((a, b) => a.tabName.localeCompare(b.tabName));

    // initialize tabs with services.xml
    const tabsContent = [{
        "tabName": "services.xml",
        "payload": vespaState.applicationPackage.servicesContent,
        "contentType": "xml"
    }]

    // possibly add hosts.xml
    let hostsContent = vespaState.applicationPackage.hostsContent;
    if (hostsContent) {
        tabsContent.push({
            "tabName": "hosts.xml",
            "payload": hostsContent,
            "contentType": "xml"
        })
    }
    
    // possibly add query-profiles with their XML content
    let queryProfilesContent = vespaState.applicationPackage.queryProfilesContent;
    // queryProfilesContent is a Map<String, String>
    if (Object.keys(queryProfilesContent).length > 0) {
        // Create a combined payload with all query profile XML contents
        const combinedQueryProfiles = Object.entries(queryProfilesContent)
            .map(([profileName, content]) => {
                if (content) {
                    return `<!-- ${profileName} -->\n${content}`;
                } else {
                    return `<!-- ${profileName} -->\n<!-- Error loading query profile -->`;
                }
            })
            .join('\n\n');
            
        tabsContent.push({
            "tabName": "query-profiles",
            "payload": combinedQueryProfiles,
            "contentType": "xml"
        })
    }
    
    // possibly add query-profile-types with their XML content
    let queryProfileTypesContent = vespaState.applicationPackage.queryProfileTypesContent;
    // queryProfileTypesContent is a Map<String, String>
    if (Object.keys(queryProfileTypesContent).length > 0) {
        // Create a combined payload with all query profile type XML contents
        const combinedQueryProfileTypes = Object.entries(queryProfileTypesContent)
            .map(([profileTypeName, content]) => {
                if (content) {
                    return `<!-- ${profileTypeName} -->\n${content}`;
                } else {
                    return `<!-- ${profileTypeName} -->\n<!-- Error loading query profile type -->`;
                }
            })
            .join('\n\n');
            
        tabsContent.push({
            "tabName": "query-profile-types",
            "payload": combinedQueryProfileTypes,
            "contentType": "xml"
        })
    }
    
    // add the schemas
    tabsContent.push(...schemas)

    // Add new lazy-loading app package explorer
    // This fetches files on-demand to avoid OOM
    // Pass configHost which the backend will convert to Raw App URL
    tabsContent.push({
        "tabName": "Raw App Package",
        "payload": vespaState.configHost,
        "contentType": "app-package-explorer"
    });

    // build common tabs
    const tabs = tabsContent
        .map(tab => {
                return {
                    "header": tab.tabName,
                    "content":
                        tab.contentType === "app-package-explorer" ? (
                            <div className="p-4">
                                <AppPackageExplorer configHost={tab.payload} />
                            </div>
                        ) : tab.contentType === "schema-definition" ? (
                            <SchemaDefinition
                                schemaContent={tab.schemaContent}
                                rankProfiles={tab.rankProfiles}
                            />
                        ) : (
                            <SyntaxHighlighter language={tab.contentType} style={androidstudio}>
                                {tab.payload}
                            </SyntaxHighlighter>
                        )
                }
            }
        )

    // build 'about'. This is done separately since it builds a different component inside the tab.
    tabs.push({
        "header": "about",
        "content":
            <div className="mt-8 mb-3">
                <p><span
                    className="text-yellow-400">Generation:</span> {vespaState.applicationPackage.appPackageGeneration}
                </p>
                <p><span
                    className="text-yellow-400">Version:</span> {`${vespaState.vespaVersion.major}.${vespaState.vespaVersion.minor}.${vespaState.vespaVersion.patch}`}
                </p>
            </div>
    })

    return (
        <div className="flex-1 max-h-full max-w-full bg-darkest-blue">
            <div className="flex flex-grow flex-col pt-2 normal-case" style={{minWidth: "0"}}>
                <div className="bg-standout-blue p-4 mt-4">
                    <TabView tabs={tabs}></TabView>
                </div>

            </div>
        </div>
    );
}

export default AppPackage;
