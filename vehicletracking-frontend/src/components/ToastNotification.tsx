import React from 'react';
import { AlertTriangle, CheckCircle, Info, X } from 'lucide-react';
import { ToastItem } from '../types';

interface ToastNotificationProps {
  toasts: ToastItem[];
  onDismiss: (id: string) => void;
}

export default function ToastNotification({ toasts, onDismiss }: ToastNotificationProps) {
  if (!toasts || toasts.length === 0) return null;

  return (
    <div className="toast-container">
      {toasts.map((toast) => {
        const isCheckIn = toast.type === 'CHECK_IN';
        const isDanger = toast.level === 'DANGER' || toast.type === 'DELAY_ALERT';
        const isWarning = toast.level === 'WARNING';

        return (
          <div
            key={toast.id}
            className={`toast-item ${isDanger ? 'DANGER' : isWarning ? 'WARNING' : 'INFO'}`}
          >
            <div style={{ marginTop: '2px' }}>
              {isCheckIn ? (
                <CheckCircle size={20} color="#10b981" />
              ) : isDanger ? (
                <AlertTriangle size={20} color="#ef4444" />
              ) : isWarning ? (
                <AlertTriangle size={20} color="#f59e0b" />
              ) : (
                <Info size={20} color="#00f0ff" />
              )}
            </div>

            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                <h4 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#fff' }}>
                  {toast.title}
                </h4>
                <span style={{ fontSize: '0.7rem', color: '#64748b' }}>
                  {toast.time || 'Vừa xong'}
                </span>
              </div>
              <p style={{ fontSize: '0.8rem', color: '#cbd5e1', lineHeight: '1.4' }}>
                {toast.message}
              </p>
            </div>

            <button
              onClick={() => onDismiss(toast.id)}
              style={{ background: 'transparent', border: 'none', color: '#64748b', cursor: 'pointer', padding: '2px' }}
            >
              <X size={16} />
            </button>
          </div>
        );
      })}
    </div>
  );
}
