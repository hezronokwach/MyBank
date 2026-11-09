import { useState, useEffect } from 'react';
import api from '../../api/axiosInstance';

// "1234567" becomes "1,234,567"
function formatNumber(value) {
    return Number(value || 0).toLocaleString();
}

// Small amounts need more decimals: 0.00035 would show as $0.00 otherwise
function formatCost(value) {
    const amount = Number(value || 0);
    if (amount < 0.01) {
        return '$' + amount.toFixed(5);
    }
    return '$' + amount.toFixed(2);
}

function formatDate(isoText) {
    if (!isoText) {
        return '';
    }
    return new Date(isoText).toLocaleString();
}

// One box with a big number, a label and an optional line underneath
function MetricCard({ label, value, note }) {
    return (
        <div className="bg-white border border-gray-100 rounded-2xl p-5 shadow-sm">
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wider">{label}</p>
            <p className="text-2xl font-extrabold text-bank-heading mt-2">{value}</p>
            {note && <p className="text-xs text-gray-500 mt-1">{note}</p>}
        </div>
    );
}

// A group of cards with a heading
function MetricGroup({ title, children }) {
    return (
        <div className="mb-6">
            <h3 className="text-sm font-bold text-gray-700 mb-3">{title}</h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                {children}
            </div>
        </div>
    );
}

// The assistant's numbers for admins. They come from GET /api/v1/admin/assistant/metrics
// and start again from 0 every time the backend restarts.
const AssistantMetricsPanel = () => {
    const [metrics, setMetrics] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [hasError, setHasError] = useState(false);

    const loadMetrics = async () => {
        setIsLoading(true);
        setHasError(false);
        try {
            const response = await api.get('/admin/assistant/metrics');
            setMetrics(response.data);
        } catch (error) {
            console.error('Failed to fetch assistant metrics', error);
            setHasError(true);
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadMetrics();
    }, []);

    let content;
    if (hasError) {
        content = <p className="text-sm text-red-600">Couldn't load the assistant metrics. Press Refresh to try again.</p>;
    } else if (metrics === null) {
        content = <p className="text-sm text-gray-500">Loading…</p>;
    } else {
        const totalAnswers = metrics.answersOk + metrics.answersFailed + metrics.answersProviderLimited;
        const totalRatings = metrics.thumbsUp + metrics.thumbsDown;

        let ratingNote = 'No ratings yet';
        if (totalRatings > 0) {
            ratingNote = Math.round((metrics.thumbsUp / totalRatings) * 100) + '% thumbs up';
        }

        content = (
            <>
                <MetricGroup title="Answers">
                    <MetricCard label="Answered" value={formatNumber(metrics.answersOk)} note={'of ' + formatNumber(totalAnswers) + ' questions'} />
                    <MetricCard label="Failed" value={formatNumber(metrics.answersFailed)} note="Model error or timeout" />
                    <MetricCard label="Groq limit reached" value={formatNumber(metrics.answersProviderLimited)} note="Groq's own usage limit" />
                    <MetricCard label="Average time" value={(metrics.averageLatencyMs / 1000).toFixed(1) + ' s'} note="Answers that worked" />
                </MetricGroup>

                <MetricGroup title="Usage">
                    <MetricCard label="Input tokens" value={formatNumber(metrics.inputTokens)} />
                    <MetricCard label="Output tokens" value={formatNumber(metrics.outputTokens)} />
                    <MetricCard label="Estimated cost" value={formatCost(metrics.estimatedCostUsd)} note="On a paid plan; the free tier is $0" />
                    <MetricCard label="Tool calls" value={formatNumber(metrics.toolCalls)} note={formatNumber(metrics.toolCallsNotOk) + ' not found or denied'} />
                </MetricGroup>

                <MetricGroup title="Safety">
                    <MetricCard label="Too many messages" value={formatNumber(metrics.blockedRateLimit)} note="Over 10 a minute" />
                    <MetricCard label="Tool limit hit" value={formatNumber(metrics.blockedToolLimit)} note="Over 5 tool calls in one answer" />
                    <MetricCard label="Hidden by output guard" value={formatNumber(metrics.blockedOutputGuard)} note="Account number, PIN or token removed" />
                </MetricGroup>

                <MetricGroup title="Confirm cards and feedback">
                    <MetricCard label="Freeze requests" value={formatNumber(metrics.actionsCreated)}
                        note={metrics.actionsConfirmed + ' confirmed, ' + metrics.actionsCancelled + ' cancelled, ' + metrics.actionsExpired + ' expired, ' + metrics.actionsFailed + ' failed'} />
                    <MetricCard label="Thumbs up" value={formatNumber(metrics.thumbsUp)} note={ratingNote} />
                    <MetricCard label="Thumbs down" value={formatNumber(metrics.thumbsDown)} />
                </MetricGroup>
            </>
        );
    }

    return (
        <section className="bg-gray-50 border border-gray-100 rounded-3xl p-6 mb-8">
            <div className="flex items-start justify-between gap-4 mb-6">
                <div>
                    <h2 className="text-xl font-extrabold text-bank-heading">Assistant</h2>
                    <p className="text-sm text-gray-500 mt-1">
                        Totals for all customers
                        {metrics && <> since {formatDate(metrics.countingSince)} (the last restart)</>}
                    </p>
                </div>
                <button
                    type="button"
                    onClick={loadMetrics}
                    disabled={isLoading}
                    className="px-4 py-2 text-sm text-indigo-700 bg-indigo-50 rounded-xl hover:bg-indigo-100 transition font-bold"
                >
                    {isLoading ? 'Loading…' : 'Refresh'}
                </button>
            </div>
            {content}
        </section>
    );
};

export default AssistantMetricsPanel;
