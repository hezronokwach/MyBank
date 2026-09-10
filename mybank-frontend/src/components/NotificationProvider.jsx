import { useCallback, useState } from 'react';
import { NotificationContext } from './notificationContext';

export function NotificationProvider({ children }) {
    const [notifications, setNotifications] = useState([]);

    const notify = useCallback((message, type = 'success') => {
        const id = crypto.randomUUID();
        setNotifications((current) => [...current, { id, message, type }]);
        window.setTimeout(() => {
            setNotifications((current) => current.filter((notification) => notification.id !== id));
        }, 4500);
    }, []);

    const dismiss = useCallback((id) => {
        setNotifications((current) => current.filter((notification) => notification.id !== id));
    }, []);

    return (
        <NotificationContext.Provider value={{ notify }}>
            {children}
            <div className="toast-region" aria-live="polite" aria-atomic="true">
                {notifications.map(({ id, message, type }) => (
                    <div key={id} className={`toast toast--${type}`} role="status">
                        <span className="toast__icon" aria-hidden="true">{type === 'success' ? '✓' : '!'}</span>
                        <span>{message}</span>
                        <button type="button" onClick={() => dismiss(id)} className="toast__dismiss" aria-label="Dismiss notification">×</button>
                    </div>
                ))}
            </div>
        </NotificationContext.Provider>
    );
}
