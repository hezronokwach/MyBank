import { useState } from 'react';

// A card for something the assistant prepared, such as "Freeze account ...4821" or "Send KES 500.00 ...".
// Nothing happens until the customer presses Confirm. The AI is not involved in this step:
// the buttons call the confirm and cancel endpoints directly.
// A transfer also needs the PIN, typed here. It is sent only to the confirm endpoint, never to the chat.
const PendingActionCard = ({ action, isBusy, onConfirm, onCancel }) => {
    const [pin, setPin] = useState('');
    const isWaiting = action.status === 'PENDING';
    const needsPin = action.type === 'TRANSFER';

    const handleConfirm = () => {
        onConfirm(action.id, pin);
        setPin('');
    };

    let statusText = '';
    if (action.status === 'CONFIRMED') {
        statusText = 'Done';
    } else if (action.status === 'CANCELLED') {
        statusText = 'Cancelled';
    } else if (action.status === 'EXPIRED') {
        statusText = 'Expired';
    } else if (action.status === 'FAILED') {
        statusText = 'Could not be done';
    }

    return (
        <div className="mt-3 border border-amber-300 bg-amber-50 rounded-lg p-4">
            <p className="text-xs text-amber-800 uppercase tracking-wider font-semibold">Needs your confirmation</p>
            <p className="font-semibold text-[#183a62] mt-1">{action.summary}</p>

            {isWaiting && (
                <>
                    <p className="text-sm text-gray-600 mt-1">Nothing has changed yet. This request expires in 5 minutes.</p>
                    {needsPin && (
                        <div className="mt-3">
                            <label htmlFor={`pin-${action.id}`} className="block text-xs font-semibold text-gray-700 mb-1">Your PIN</label>
                            <input
                                id={`pin-${action.id}`}
                                type="password"
                                inputMode="numeric"
                                autoComplete="off"
                                maxLength="4"
                                value={pin}
                                onChange={(e) => setPin(e.target.value)}
                                className="w-28 border border-slate-300 rounded px-3 py-1.5 text-sm bg-white"
                            />
                            <p className="text-xs text-gray-500 mt-1">Type your PIN here only. Never send it in the chat.</p>
                        </div>
                    )}
                    <div className="flex gap-3 mt-3">
                        <button
                            type="button"
                            onClick={handleConfirm}
                            disabled={isBusy || (needsPin && pin.length < 4)}
                            className="px-4 py-2 text-white bg-[#183a62] rounded hover:bg-[#102e50] transition font-semibold text-sm"
                        >
                            {isBusy ? 'Working…' : 'Confirm'}
                        </button>
                        <button
                            type="button"
                            onClick={() => onCancel(action.id)}
                            disabled={isBusy}
                            className="px-4 py-2 text-[#183a62] bg-white border border-slate-300 rounded hover:bg-slate-50 transition font-semibold text-sm"
                        >
                            Cancel
                        </button>
                    </div>
                </>
            )}

            {!isWaiting && (
                <p className="text-sm mt-2">
                    <span className="font-semibold">{statusText}.</span> {action.result}
                </p>
            )}
        </div>
    );
};

export default PendingActionCard;
