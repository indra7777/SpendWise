import { useState } from 'react'
import { Smartphone, Wifi, Brain, Database, Bell, Moon, Shield, ExternalLink } from 'lucide-react'

export default function SettingsPage() {
  const [darkMode, setDarkMode] = useState(false)
  const [notifications, setNotifications] = useState(true)

  return (
    <div className="space-y-6 pb-20 md:pb-0">
      <h2 className="text-2xl font-bold text-gray-900">Settings</h2>

      {/* Connection Status */}
      <div className="card">
        <div className="flex items-center space-x-3 mb-4">
          <Smartphone className="text-primary-500" size={24} />
          <h3 className="font-semibold text-gray-900">Device Connection</h3>
        </div>
        <div className="flex items-center justify-between p-4 bg-green-50 rounded-xl">
          <div className="flex items-center space-x-3">
            <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
            <div>
              <p className="font-medium text-green-800">Connected</p>
              <p className="text-sm text-green-600">Receiving data from RupeeLog app</p>
            </div>
          </div>
          <Wifi className="text-green-500" size={24} />
        </div>
      </div>

      {/* AI Settings */}
      <div className="card">
        <div className="flex items-center space-x-3 mb-4">
          <Brain className="text-purple-500" size={24} />
          <h3 className="font-semibold text-gray-900">AI Configuration</h3>
        </div>

        <div className="space-y-4">
          <div className="flex items-center justify-between p-4 bg-gray-50 rounded-xl">
            <div>
              <p className="font-medium text-gray-900">Local AI (Gemma 3n)</p>
              <p className="text-sm text-gray-500">On-device categorization</p>
            </div>
            <div className="flex items-center space-x-2">
              <span className="text-xs bg-green-100 text-green-700 px-2 py-1 rounded-full">Active</span>
            </div>
          </div>

          <div className="flex items-center justify-between p-4 bg-gray-50 rounded-xl">
            <div>
              <p className="font-medium text-gray-900">Cloud AI (Gemini)</p>
              <p className="text-sm text-gray-500">Advanced insights & reports</p>
            </div>
            <div className="flex items-center space-x-2">
              <span className="text-xs bg-yellow-100 text-yellow-700 px-2 py-1 rounded-full">API Key Required</span>
            </div>
          </div>
        </div>
      </div>

      {/* Preferences */}
      <div className="card">
        <h3 className="font-semibold text-gray-900 mb-4">Preferences</h3>

        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <Bell className="text-gray-400" size={20} />
              <div>
                <p className="font-medium text-gray-900">Notifications</p>
                <p className="text-sm text-gray-500">Budget alerts & insights</p>
              </div>
            </div>
            <button
              onClick={() => setNotifications(!notifications)}
              className={`relative w-12 h-6 rounded-full transition-colors ${
                notifications ? 'bg-primary-500' : 'bg-gray-300'
              }`}
            >
              <span
                className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
                  notifications ? 'left-7' : 'left-1'
                }`}
              ></span>
            </button>
          </div>

          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <Moon className="text-gray-400" size={20} />
              <div>
                <p className="font-medium text-gray-900">Dark Mode</p>
                <p className="text-sm text-gray-500">Toggle dark theme</p>
              </div>
            </div>
            <button
              onClick={() => setDarkMode(!darkMode)}
              className={`relative w-12 h-6 rounded-full transition-colors ${
                darkMode ? 'bg-primary-500' : 'bg-gray-300'
              }`}
            >
              <span
                className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
                  darkMode ? 'left-7' : 'left-1'
                }`}
              ></span>
            </button>
          </div>
        </div>
      </div>

      {/* Data Management */}
      <div className="card">
        <div className="flex items-center space-x-3 mb-4">
          <Database className="text-blue-500" size={24} />
          <h3 className="font-semibold text-gray-900">Data Management</h3>
        </div>

        <div className="space-y-3">
          <button className="w-full flex items-center justify-between p-4 bg-gray-50 hover:bg-gray-100 rounded-xl transition-colors">
            <span className="font-medium text-gray-900">Export Transactions</span>
            <ExternalLink size={20} className="text-gray-400" />
          </button>

          <button className="w-full flex items-center justify-between p-4 bg-gray-50 hover:bg-gray-100 rounded-xl transition-colors">
            <span className="font-medium text-gray-900">Import Statement</span>
            <ExternalLink size={20} className="text-gray-400" />
          </button>
        </div>
      </div>

      {/* Privacy */}
      <div className="card">
        <div className="flex items-center space-x-3 mb-4">
          <Shield className="text-green-500" size={24} />
          <h3 className="font-semibold text-gray-900">Privacy & Security</h3>
        </div>

        <div className="p-4 bg-green-50 rounded-xl">
          <p className="text-sm text-green-800">
            <strong>Your data is secure.</strong> All financial data is stored locally on your device.
            The dashboard only connects to your phone over your local network.
          </p>
        </div>
      </div>

      {/* About */}
      <div className="card text-center">
        <p className="text-gray-600">RupeeLog Dashboard</p>
        <p className="text-sm text-gray-400">Version 1.0.0</p>
      </div>
    </div>
  )
}
