import { useState, useEffect } from 'react';
import api from '../../api/axiosInstance';
import { useNotification } from '../notificationContext';
import { errorMessage, formatExpiry, formatMoney, groupCardNumber } from './cardHelpers';

const SHOW_FOR_SECONDS = 30;

// The card picture. "Show details" asks for the PIN, then shows the number and CVV for 30 seconds.
const VirtualCard = ({ card, ownerName }) => {
    const [isAskingPin, setIsAskingPin] = useState(false);
    const [pin, setPin] = useState('');
    const [isChecking, setIsChecking] = useState(false);
    const [details, setDetails] = useState(null);
    const [secondsLeft, setSecondsLeft] = useState(0);
    const { notify } = useNotification();

    // Counts down once a second while the details are shown, then hides them again
    useEffect(() => {
        if (details === null) {
            return;
        }
        const timer = setTimeout(() => {
            if (secondsLeft <= 1) {
                setDetails(null);
                setSecondsLeft(0);
            } else {
                setSecondsLeft(secondsLeft - 1);
            }
        }, 1000);
        return () => clearTimeout(timer);
    }, [details, secondsLeft]);

    const handleShowDetails = async (e) => {
        e.preventDefault();
        setIsChecking(true);
        try {
            const response = await api.post(`/cards/${card.id}/details`, { pin: pin });
            setDetails(response.data);
            setSecondsLeft(SHOW_FOR_SECONDS);
            setIsAskingPin(false);
        } catch (error) {
            notify(errorMessage(error, 'We could not show the card details.'), 'error');
        } finally {
            // The PIN never stays on the screen
            setPin('');
            setIsChecking(false);
        }
    };

    const hideDetails = () => {
        setDetails(null);
        setSecondsLeft(0);
    };

    let number = '•••• •••• •••• ' + card.last4;
    let cvv = '•••';
    if (details !== null) {
        number = groupCardNumber(details.cardNumber);
        cvv = details.cvv;
    }

    let cardClass = 'virtual-card';
    if (card.status === 'FROZEN') {
        cardClass = cardClass + ' virtual-card--frozen';
    }

    return (
        <div className="virtual-card-wrap">
            <div className={cardClass} aria-label="Your MyBank card">
                <div className="virtual-card__top">
                    <span className="virtual-card__brand">MyBank</span>
                    <span className="virtual-card__label">{card.status === 'FROZEN' ? 'Frozen' : 'Virtual'}</span>
                </div>
                <div className="virtual-card__chip" aria-hidden="true"></div>
                <p className="virtual-card__number">{number}</p>
                <div className="virtual-card__bottom">
                    <div>
                        <span className="virtual-card__small">Card holder</span>
                        <strong>{ownerName}</strong>
                    </div>
                    <div>
                        <span className="virtual-card__small">Expires</span>
                        <strong>{formatExpiry(card.expiryMonth, card.expiryYear)}</strong>
                    </div>
                    <div>
                        <span className="virtual-card__small">CVV</span>
                        <strong>{cvv}</strong>
                    </div>
                </div>
            </div>

            <div className="virtual-card__balance">
                <span>Card balance</span>
                <strong>{formatMoney(card.balance, card.currency)}</strong>
            </div>

            {details !== null && (
                <div className="reveal-box">
                    <span>Hiding in {secondsLeft} s</span>
                    <button type="button" className="button button--secondary button--small" onClick={hideDetails}>Hide now</button>
                </div>
            )}

            {details === null && !isAskingPin && (
                <button type="button" className="button button--secondary card-panel__wide" onClick={() => setIsAskingPin(true)}>
                    Show card details
                </button>
            )}

            {details === null && isAskingPin && (
                <form className="inline-form reveal-form" onSubmit={handleShowDetails}>
                    <label htmlFor="reveal-pin">Enter your PIN to see the full number and CVV</label>
                    <div className="reveal-form__row">
                        <input id="reveal-pin" type="password" inputMode="numeric" autoComplete="off" maxLength="4"
                            value={pin} onChange={(e) => setPin(e.target.value)} autoFocus required />
                        <button type="submit" className="button button--primary" disabled={isChecking || pin.length < 4}>
                            {isChecking ? 'Checking…' : 'Show'}
                        </button>
                        <button type="button" className="button button--secondary" onClick={() => { setIsAskingPin(false); setPin(''); }}>
                            Cancel
                        </button>
                    </div>
                </form>
            )}
        </div>
    );
};

export default VirtualCard;
