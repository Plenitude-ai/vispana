import React, {useState} from 'react';

function FileExplorer({node}) {
    const [expanded, setExpanded] = useState(false);

    if (node.type === 'file') {
        return (
            <div className="pl-4 text-white">
                📄 {node.name}
                <pre className="bg-gray-900 p-2 mt-1 text-sm">{node.content}</pre>
            </div>
        );
    }

    return (
        <div className="pl-4 text-white">
            <div className="cursor-pointer" onClick={() => setExpanded(!expanded)}>
                📁 {node.name}
            </div>
            {expanded && node.children && (
                <div className="ml-4 border-l border-gray-600 pl-2">
                    {node.children.map((child, index) => (
                        <FileExplorer key={index} node={child}/>
                    ))}
                </div>
            )}
        </div>
    );
}

export default FileExplorer;
