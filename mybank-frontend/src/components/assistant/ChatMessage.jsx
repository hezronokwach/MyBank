import { Link } from 'react-router-dom';
import PendingActionCard from './PendingActionCard';

// The assistant is told to write plain text, but sometimes still marks words as bold
// with ** or __. Those marks would show as raw characters here, so they are removed.
function removeBoldMarks(text) {
    if (!text) {
        return text;
    }
    return text.replaceAll('**', '').replaceAll('__', '');
}

// One message in the chat: the customer's question, or the assistant's answer.
// Under an answer: what it was based on, thumbs up/down, and any confirm cards.
const ChatMessage = ({ message, busyActionId, onRate, onConfirm, onCancel }) => {
    if (message.role === 'user') {
        return (
            <div className="flex justify-end">
                <div className="max-w-[80%] bg-[#183a62] text-white rounded-lg px-4 py-2 whitespace-pre-wrap">
                    {message.text}
                </div>
            </div>
        );
    }

    // The assistant couldn't answer: point to the normal pages instead
    const isFallback = message.outcome === 'FALLBACK' || message.outcome === 'RATE_LIMITED_PROVIDER';

    return (
        <div className="flex justify-start">
            <div className="max-w-[80%] bg-white border border-gray-200 rounded-lg px-4 py-3">
                <p className="text-bank-text whitespace-pre-wrap">{removeBoldMarks(message.text)}</p>

                {isFallback && (
                    <p className="text-sm mt-2">
                        <Link to="/transfer" className="text-[#235a8a] font-semibold">Make a transfer</Link>
                        {' · '}
                        <Link to="/deposit" className="text-[#235a8a] font-semibold">Deposit</Link>
                        {' · '}
                        <Link to="/withdraw" className="text-[#235a8a] font-semibold">Withdraw</Link>
                    </p>
                )}

                {message.sources && message.sources.length > 0 && (
                    <p className="text-xs text-gray-500 mt-2">Based on: {message.sources.join(', ')}</p>
                )}

                {message.pendingActions && message.pendingActions.map((action) => (
                    <PendingActionCard
                        key={action.id}
                        action={action}
                        isBusy={busyActionId === action.id}
                        onConfirm={onConfirm}
                        onCancel={onCancel}
                    />
                ))}

                {/* Thumbs up / down, only for real answers that were saved */}
                {message.messageId && message.outcome === 'OK' && (
                    <div className="flex gap-2 mt-2">
                        <button
                            type="button"
                            onClick={() => onRate(message.messageId, 1)}
                            aria-label="Helpful"
                            className={`text-sm px-2 py-0.5 rounded border ${message.rating === 1 ? 'bg-green-50 border-green-400' : 'border-gray-200 hover:bg-gray-50'}`}
                        >
                            👍
                        </button>
                        <button
                            type="button"
                            onClick={() => onRate(message.messageId, -1)}
                            aria-label="Not helpful"
                            className={`text-sm px-2 py-0.5 rounded border ${message.rating === -1 ? 'bg-red-50 border-red-400' : 'border-gray-200 hover:bg-gray-50'}`}
                        >
                            👎
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};

export default ChatMessage;
