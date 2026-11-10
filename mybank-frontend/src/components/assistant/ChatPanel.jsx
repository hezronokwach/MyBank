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
    const [isOpen, setIsOpen] = useState(false);
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

    // pin is only given for a transfer card; a freeze sends no body
    const handleConfirm = async (actionId, pin) => {
        setBusyActionId(actionId);
        try {
            let body = null;
            if (pin) {
                body = { pin: pin };
            }
            const response = await api.post(`/assistant/actions/${actionId}/confirm`, body);
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
        <aside className={`assistant-widget ${isOpen ? 'assistant-widget--open' : ''}`} aria-label="MyBank assistant">
            <button type="button" className="assistant-launcher" onClick={() => setIsOpen((open) => !open)} aria-expanded={isOpen}>
                <span className="assistant-launcher__spark" aria-hidden="true">✦</span>
                <span>{isOpen ? 'Close helper' : 'Need help?'}</span>
            </button>
            {isOpen && <div className="assistant-panel">
                <div className="assistant-panel__header">
                    <div><p className="eyebrow">MyBank helper</p><h2>Ask a quick question</h2></div>
                    <button type="button" onClick={() => setIsOpen(false)} aria-label="Close assistant">×</button>
                </div>

                <div className="assistant-suggestions">
                    <button type="button" onClick={() => setInput('How much is in my account?')}>My balance</button>
                    <button type="button" onClick={() => setInput('Show my last 3 transactions')}>Recent activity</button>
                </div>

                <div className="assistant-messages">
                    {messages.length === 0 && <p className="assistant-empty">I can help with balances, recent activity, or account questions. Never share your PIN here.</p>}

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

                    {isThinking && <div className="assistant-thinking"><i></i><i></i><i></i></div>}
                    <div ref={listEnd} />
                </div>

                <form onSubmit={handleSend} className="assistant-compose">
                    <input type="text" value={input} onChange={(e) => setInput(e.target.value)} maxLength={MAX_LENGTH} placeholder="Ask about your money" aria-label="Message to the assistant" disabled={isThinking} />
                    <button type="submit" disabled={isThinking || input.trim() === ''} aria-label="Send message">↑</button>
                </form>
            </div>}
        </aside>
    );
};

export default ChatPanel;
