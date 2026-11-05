import { useEffect, useRef, useState } from 'react';
import api from '../../api/axiosInstance';
import { useNotification } from '../notificationContext';
import ChatMessage from './ChatMessage';

const MAX_LENGTH = 500;

// Gives back a copy of the messages with one pending action replaced by its new version
// (for example after it was confirmed), so React redraws that card.
function replaceAction(messages, updatedAction) {
    const newMessages = [];
    for (const message of messages) {
        if (!message.pendingActions) {
            newMessages.push(message);
            continue;
        }
        const newActions = [];
        for (const action of message.pendingActions) {
            if (action.id === updatedAction.id) {
                newActions.push(updatedAction);
            } else {
                newActions.push(action);
            }
        }
        newMessages.push({ ...message, pendingActions: newActions });
    }
    return newMessages;
}

// Gives back a copy of the messages with the rating set on one answer
function setRating(messages, messageId, rating) {
    const newMessages = [];
    for (const message of messages) {
        if (message.messageId === messageId) {
            newMessages.push({ ...message, rating: rating });
        } else {
            newMessages.push(message);
        }
    }
    return newMessages;
}

// The assistant chat on the Dashboard.
// onAccountsChanged is called after a confirmed action (such as a freeze), so the Dashboard reloads the accounts.
const ChatPanel = ({ onAccountsChanged }) => {
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [conversationId, setConversationId] = useState(null);
    const [isThinking, setIsThinking] = useState(false);
    const [busyActionId, setBusyActionId] = useState(null);
    const listEnd = useRef(null);
    const { notify } = useNotification();

    // Keep the newest message in view
    useEffect(() => {
        if (listEnd.current) {
            listEnd.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
    }, [messages, isThinking]);

    const handleSend = async (e) => {
        e.preventDefault();
        const text = input.trim();
        if (text === '' || isThinking) return;

        setMessages((current) => [...current, { role: 'user', text: text }]);
        setInput('');
        setIsThinking(true);

        try {
            const response = await api.post('/assistant/chat', { conversationId: conversationId, message: text });
            const answer = response.data;
            setConversationId(answer.conversationId);
            setMessages((current) => [...current, { role: 'assistant', ...answer }]);
        } catch (error) {
            // Too many messages in a minute: the answer still comes back, with status 429
            if (error.response?.status === 429 && error.response.data?.text) {
                setMessages((current) => [...current, { role: 'assistant', ...error.response.data }]);
            } else {
                notify(error.response?.data?.message || 'The assistant could not be reached. Please try again.', 'error');
            }
        } finally {
            setIsThinking(false);
        }
    };

    const handleConfirm = async (actionId) => {
        setBusyActionId(actionId);
        try {
            const response = await api.post(`/assistant/actions/${actionId}/confirm`);
            const action = response.data;
            setMessages((current) => replaceAction(current, action));
            if (action.status === 'CONFIRMED') {
                notify(action.result);
                onAccountsChanged();
            } else {
                notify(action.result || 'The request could not be completed.', 'error');
            }
        } catch (error) {
            notify(error.response?.data?.message || 'We could not confirm this request. Please try again.', 'error');
        } finally {
            setBusyActionId(null);
        }
    };

    const handleCancel = async (actionId) => {
        setBusyActionId(actionId);
        try {
            const response = await api.post(`/assistant/actions/${actionId}/cancel`);
            setMessages((current) => replaceAction(current, response.data));
            notify('Request cancelled. Nothing was changed.');
        } catch (error) {
            notify(error.response?.data?.message || 'We could not cancel this request. Please try again.', 'error');
        } finally {
            setBusyActionId(null);
        }
    };

    const handleRate = async (messageId, rating) => {
        try {
            await api.post('/assistant/feedback', { messageId: messageId, rating: rating });
            setMessages((current) => setRating(current, messageId, rating));
        } catch (error) {
            notify(error.response?.data?.message || 'We could not save your feedback.', 'error');
        }
    };

    return (
        <div className="bg-white border border-gray-200 rounded-lg mt-8">
            <div className="px-6 py-4 border-b border-gray-100">
                <h2 className="text-lg font-bold text-bank-heading">Ask the MyBank assistant</h2>
                <p className="text-sm text-gray-500">Balances, recent transactions, or freezing an account. Never share your PIN here.</p>
            </div>

            <div className="px-6 py-4 h-96 overflow-y-auto bg-slate-50 flex flex-col gap-3">
                {messages.length === 0 && (
                    <p className="text-sm text-gray-500 text-center mt-8">
                        Try "How much is in my savings account?" or "Show my last 3 transactions".
                    </p>
                )}

                {messages.map((message, index) => (
                    <ChatMessage
                        key={message.messageId || index}
                        message={message}
                        busyActionId={busyActionId}
                        onRate={handleRate}
                        onConfirm={handleConfirm}
                        onCancel={handleCancel}
                    />
                ))}

                {isThinking && (
                    <div className="flex justify-start">
                        <div className="bg-white border border-gray-200 rounded-lg px-4 py-2 text-gray-500 text-sm">Thinking…</div>
                    </div>
                )}
                <div ref={listEnd} />
            </div>

            <form onSubmit={handleSend} className="px-6 py-4 border-t border-gray-100 flex gap-3">
                <input
                    type="text"
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    maxLength={MAX_LENGTH}
                    placeholder="Type your question…"
                    aria-label="Message to the assistant"
                    disabled={isThinking}
                    className="flex-1 border border-gray-300 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-[#183a62]"
                />
                <button
                    type="submit"
                    disabled={isThinking || input.trim() === ''}
                    className="px-5 py-2 text-white bg-[#183a62] rounded hover:bg-[#102e50] transition font-semibold"
                >
                    Send
                </button>
            </form>
        </div>
    );
};

export default ChatPanel;
