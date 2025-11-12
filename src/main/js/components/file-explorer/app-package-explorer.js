import React, { useState, useEffect } from 'react';

/**
 * App Package Explorer - Lazy-loading file browser for Vespa application package
 * 
 * Unlike the original FileExplorer, this component:
 * 1. Fetches only the tree structure initially (no file contents)
 * 2. Loads file content on-demand when user clicks on a file
 * 3. Provides a button to download the entire package as ZIP
 */
function AppPackageExplorer({ configHost }) {
    const [fileTree, setFileTree] = useState(null);
    const [loadingTree, setLoadingTree] = useState(true);
    const [error, setError] = useState(null);

    // Fetch the tree structure on mount
    useEffect(() => {
        const fetchTree = async () => {
            try {
                const params = new URLSearchParams({ config_host: configHost });
                const response = await fetch(`/api/apppackage/tree?${params}`);
                
                if (!response.ok) {
                    throw new Error(`Failed to load file tree: ${response.status}`);
                }
                
                const tree = await response.json();
                setFileTree(tree);
                setLoadingTree(false);
            } catch (err) {
                console.error('Error fetching file tree:', err);
                setError(err.message);
                setLoadingTree(false);
            }
        };

        fetchTree();
    }, [configHost]);

    // Download the full package as ZIP
    const downloadZip = () => {
        const params = new URLSearchParams({ config_host: configHost });
        // Trigger download by opening the URL
        window.location.href = `/api/apppackage/download?${params}`;
    };

    if (loadingTree) {
        return (
            <div className="p-4 text-white">
                <p>Loading file tree...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="p-4 text-red-400">
                <p>Error: {error}</p>
            </div>
        );
    }

    if (!fileTree || !fileTree.root) {
        return (
            <div className="p-4 text-white">
                <p>No files found</p>
            </div>
        );
    }

    return (
        <div className="text-white">
            <div className="mb-4 flex items-center justify-between border-b border-gray-600 pb-2">
                <div>
                    <p className="text-sm text-gray-400">
                        Files: {fileTree.totalFiles} | Directories: {fileTree.totalDirectories}
                    </p>
                </div>
                <button
                    onClick={downloadZip}
                    className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm"
                >
                    📦 Download as ZIP
                </button>
            </div>
            
            <div className="overflow-auto max-h-[600px]">
                <FileNode node={fileTree.root} configHost={configHost} />
            </div>
        </div>
    );
}

/**
 * Individual file/directory node with lazy content loading
 */
function FileNode({ node, configHost, level = 0 }) {
    const [expanded, setExpanded] = useState(false);
    const [details, setFileDetails] = useState(null);
    const [loadingDetails, setLoadingDetails] = useState(false);


    const handleClick = async () => {
        if (node.isFile) {
            // Toggle file content display
            if (expanded) {
                setExpanded(false);
            } else {
                // Fetch content if not already loaded
                if (!details && !loadingDetails) {
                    setLoadingDetails(true);
                    try {
                        const params = new URLSearchParams({
                            config_host: configHost,
                            file_path: node.path
                        });
                        const response = await fetch(`/api/apppackage/file?${params}`);
                        
                        if (!response.ok) {
                            throw new Error(`Failed to load file: ${response.status}`);
                        }
                        
                        const result = await response.json();
                        const file_url = result.url;
                        const file_content = result.content;
                        const details = { url: file_url, content: file_content };
                        setFileDetails(details);
                        setExpanded(true);
                    } catch (err) {
                        console.error('Error fetching file:', err);
                        setFileDetails(`// Error loading file: ${err.message}`);
                        setExpanded(true);
                    } finally {
                        setLoadingDetails(false);
                    }
                } else {
                    setExpanded(true);
                }
            }
        } else {
            // Toggle directory expansion
            setExpanded(!expanded);
        }
    };

    const icon = node.isFile ? '📄' : (expanded ? '📂' : '📁');
    
    return (
        <div className="text-white" style={{ marginLeft: `${level * 16}px` }}>
            <div 
                className="cursor-pointer hover:bg-gray-800 py-1 px-2 rounded"
                onClick={handleClick}
            >
                <span className="select-none">{icon}</span> {node.name}
                {loadingDetails && <span className="ml-2 text-gray-400 text-sm">(loading...)</span>}
            </div>
            
            {/* Show file content if expanded */}
            {node.isFile && expanded && details && details.url && (
                <div className="mt-2 mb-2">
                    <a 
                        href={details.url} 
                        target="_blank" 
                        rel="noopener noreferrer"
                        className="text-blue-400 hover:text-blue-300 text-xs"
                    >
                        🔗 View source URL
                    </a>
                </div>
            )}
            
            {/* Show content only for non-binary files */}
            {node.isFile && expanded && details && details.content && (
                details.url?.endsWith('.jar') ? (
                    <p className="text-gray-400 text-sm p-2">Binary file: Not displayable as text</p>
                ) : (
                    <pre className="bg-gray-900 p-2 mt-1 mb-2 text-xs overflow-x-auto rounded border border-gray-700">
                        {details.content}
                    </pre>
                )
            )}
            
            {/* Show directory children if expanded */}
            {!node.isFile && expanded && node.children && (
                <div className="border-l border-gray-600 ml-2">
                    {Object.entries(node.children)
                        .sort(([, a], [, b]) => {
                            // Sort: directories first, then files, alphabetically within each group
                            if (a.isFile === b.isFile) {
                                return a.name.localeCompare(b.name);
                            }
                            return a.isFile ? 1 : -1;
                        })
                        .map(([key, childNode]) => (
                            <FileNode 
                                key={key} 
                                node={childNode} 
                                configHost={configHost} 
                                level={level + 1} 
                            />
                        ))
                    }
                </div>
            )}
        </div>
    );
}

export default AppPackageExplorer;

